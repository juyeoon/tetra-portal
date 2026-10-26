package com.tetra.portal.monitoring;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tetra.portal.monitoring.PrometheusClient.PrometheusException;
import com.tetra.portal.monitoring.PrometheusClient.Sample;
import com.tetra.portal.monitoring.PrometheusClient.Series;

/**
 * 모니터링 갱신 1회의 Prometheus 쿼리 묶음(Console OPS-05 PromBatch 이식). 동시에 최대 {@link #PARALLELISM}개씩 보낸다.
 * 이벤트 상세는 쿼리가 30개 남짓이라 순차면 갱신 상한(8초)이 빠듯하다. Prometheus에 한꺼번에 몰리지 않게 4개로 묶는다.
 * 실패 처리(2026-10-26 리뷰):
 * - Prometheus 자체 문제(연결 거부 · 연결 시간 초과 · 5xx · 갱신 상한 초과)면 나머지를 취소하고 {@link PrometheusException}
 *   → 갱신 전체 실패(마지막 값 + stale)
 * - 그 쿼리만의 오류(4xx · 읽기 시간 초과 · 형식 오류)면 그 항목만 비우고({@link Results#ok} = false, 결과 없음) WARN 한 줄
 *   (쿼리 이름과 짧은 원인만 — 쿼리 원문 · 응답 본문 없음). 나머지는 새 값. 비운 항목은 null로 다루고 판정은 UNKNOWN
 */
final class PromBatch {

	private static final Logger log = LoggerFactory.getLogger(PromBatch.class);

	static final int PARALLELISM = 4;

	private final PrometheusClient prometheus;

	private final ExecutorService executor;

	private final long deadlineNanos;

	private final Map<String, Callable<Object>> tasks = new LinkedHashMap<>();

	PromBatch(PrometheusClient prometheus, ExecutorService executor, long deadlineNanos) {
		this.prometheus = prometheus;
		this.executor = executor;
		this.deadlineNanos = deadlineNanos;
	}

	PromBatch instant(String name, String promql, Instant at) {
		put(name, () -> prometheus.query(promql, at));
		return this;
	}

	PromBatch range(String name, String promql, Instant start, Instant end, Duration step) {
		put(name, () -> prometheus.queryRange(promql, start, end, step));
		return this;
	}

	private void put(String name, Callable<Object> task) {
		if (tasks.putIfAbsent(name, task) != null) {
			throw new IllegalArgumentException("같은 이름의 쿼리: " + name);
		}
	}

	/** 결과. 이름으로 꺼낸다. 실패한 쿼리(failed)는 빈 결과 — 0/null 규칙에서 "시리즈 없음 = 0"을 쓰기 전에 {@link #ok}를 본다 */
	record Results(Map<String, Object> values, Set<String> failed) {

		@SuppressWarnings("unchecked")
		List<Sample> instant(String name) {
			return failed.contains(name) ? List.of() : (List<Sample>) require(name);
		}

		@SuppressWarnings("unchecked")
		List<Series> range(String name) {
			return failed.contains(name) ? List.of() : (List<Series>) require(name);
		}

		/** 요청했고 성공한 쿼리 */
		boolean ok(String name) {
			return values.containsKey(name) && !failed.contains(name);
		}

		boolean has(String name) {
			return values.containsKey(name) || failed.contains(name);
		}

		/** 모두 성공 */
		boolean allOk(String... names) {
			for (String name : names) {
				if (!ok(name)) {
					return false;
				}
			}
			return true;
		}

		private Object require(String name) {
			Object value = values.get(name);
			if (value == null) {
				throw new IllegalArgumentException("요청하지 않은 쿼리: " + name);
			}
			return value;
		}

	}

	Results execute() {
		Semaphore permits = new Semaphore(PARALLELISM);
		Map<String, Future<Object>> futures = new LinkedHashMap<>();
		for (Map.Entry<String, Callable<Object>> task : tasks.entrySet()) {
			futures.put(task.getKey(), executor.submit(() -> {
				if (!permits.tryAcquire(remainingNanos(), TimeUnit.NANOSECONDS)) {
					throw new PrometheusException("갱신 시간 초과");
				}
				try {
					if (remainingNanos() <= 0) {
						throw new PrometheusException("갱신 시간 초과");
					}
					return task.getValue().call();
				}
				finally {
					permits.release();
				}
			}));
		}
		Map<String, Object> values = new LinkedHashMap<>();
		Set<String> failed = new LinkedHashSet<>();
		for (Map.Entry<String, Future<Object>> future : futures.entrySet()) {
			try {
				values.put(future.getKey(), future.getValue().get(Math.max(0, remainingNanos()), TimeUnit.NANOSECONDS));
			}
			catch (TimeoutException ex) {
				cancel(futures.values());
				throw new PrometheusException("갱신 시간 초과");
			}
			catch (ExecutionException ex) {
				if (ex.getCause() instanceof PrometheusException p && !p.serverFailure()) {
					failed.add(future.getKey());
					log.warn("Prometheus 쿼리 실패({}: {}) — 그 항목만 비웁니다", future.getKey(), p.getMessage());
					continue;
				}
				cancel(futures.values());
				if (ex.getCause() instanceof PrometheusException p) {
					throw p;
				}
				throw new PrometheusException(ex.getCause() == null ? "조회 실패" : ex.getCause().getClass().getSimpleName());
			}
			catch (InterruptedException ex) {
				cancel(futures.values());
				Thread.currentThread().interrupt();
				throw new PrometheusException("중단됨");
			}
		}
		return new Results(values, failed);
	}

	private long remainingNanos() {
		return deadlineNanos - System.nanoTime();
	}

	private static void cancel(Iterable<Future<Object>> futures) {
		List<Future<Object>> all = new ArrayList<>();
		futures.forEach(all::add);
		all.forEach(f -> f.cancel(true));
	}

}
