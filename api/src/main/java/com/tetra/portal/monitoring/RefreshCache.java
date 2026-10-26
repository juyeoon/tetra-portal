package com.tetra.portal.monitoring;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tetra.portal.monitoring.PrometheusClient.PrometheusException;

/**
 * 조회 결과 하나를 캐시한다(Console RefreshCache 이식). 모니터링은 Prometheus 결과(Metrics)만 넣는다 — 상태 · 시각은 요청마다 계산.
 * - TTL 안에는 마지막 값을 그대로 준다. Pod마다 따로 두는 조회 캐시다 — Pod끼리 값이 몇 초 달라도 되고 공유 상태가 아니다(ECS 태스크마다 따로).
 * - single-flight: 같은 시점의 요청은 진행 중인 갱신 하나를 같이 기다린다(조회는 한 번).
 * - 갱신 1회 전체 상한을 넘거나 실패하면 마지막 값을 stale로(asStale), 없으면 unavailable.
 *   실패한 뒤 TTL 동안은 다시 조회하지 않는다(외부 장애 때 요청마다 상한까지 기다리지 않게).
 * - 실패 로그는 갱신마다 WARN 한 줄(원인만, 쿼리 원문 · 응답 본문 · 자격 증명 없음).
 */
final class RefreshCache<T> {

	private static final Logger log = LoggerFactory.getLogger(RefreshCache.class);

	/** 갱신 1회. deadlineNanos = System.nanoTime() 기준 상한 */
	@FunctionalInterface
	interface Loader<T> {

		T load(long deadlineNanos);

	}

	private final String label;

	private final long ttlNanos;

	private final Duration refreshTimeout;

	private final ExecutorService executor;

	private final Loader<T> loader;

	private final UnaryOperator<T> asStale;

	private final Supplier<T> unavailable;

	private final Object lock = new Object();

	// ---- lock으로 보호 ----

	private CompletableFuture<T> inFlight;

	private T last;

	private long lastSuccessNanos;

	private boolean failedRecently;

	private long lastFailureNanos;

	private long lastUsedNanos = System.nanoTime();

	/**
	 * @param label 로그용 이름(예: "모니터링 · Prometheus")
	 * @param asStale 마지막 값을 stale로 표시한 값
	 * @param unavailable 캐시가 없을 때 줄 값(던지면 그 예외가 호출한 쪽으로 간다)
	 */
	RefreshCache(String label, Duration ttl, Duration refreshTimeout, ExecutorService executor, Loader<T> loader,
			UnaryOperator<T> asStale, Supplier<T> unavailable) {
		this.label = label;
		this.ttlNanos = ttl.toNanos();
		this.refreshTimeout = refreshTimeout;
		this.executor = executor;
		this.loader = loader;
		this.asStale = asStale;
		this.unavailable = unavailable;
	}

	T get() {
		CompletableFuture<T> refresh;
		synchronized (lock) {
			long now = System.nanoTime();
			lastUsedNanos = now;
			if (last != null && now - lastSuccessNanos < ttlNanos) {
				return last;
			}
			if (inFlight != null) {
				refresh = inFlight;
			}
			else {
				if (failedRecently && now - lastFailureNanos < ttlNanos) {
					return fallbackLocked();
				}
				long deadline = now + refreshTimeout.toNanos();
				CompletableFuture<T> started = CompletableFuture.supplyAsync(() -> loader.load(deadline), executor)
					.orTimeout(refreshTimeout.toMillis(), TimeUnit.MILLISECONDS);
				inFlight = started;
				// 이미 끝났으면 이 스레드에서 바로 finish가 돈다(같은 lock, 재진입)
				started.whenComplete((result, error) -> finish(started, result, error));
				refresh = started;
			}
		}
		try {
			return refresh.join();
		}
		catch (CompletionException ex) {
			synchronized (lock) {
				return fallbackLocked();
			}
		}
	}

	/** 마지막으로 get()을 부른 시각(System.nanoTime) — 키별 캐시 정리용 */
	long lastUsedNanos() {
		synchronized (lock) {
			return lastUsedNanos;
		}
	}

	private void finish(CompletableFuture<T> refresh, T result, Throwable error) {
		synchronized (lock) {
			if (inFlight == refresh) {
				inFlight = null;
			}
			if (error == null) {
				last = result;
				lastSuccessNanos = System.nanoTime();
				failedRecently = false;
				return;
			}
			failedRecently = true;
			lastFailureNanos = System.nanoTime();
		}
		log.warn("{} 조회 실패({}) — 마지막 값을 stale로 응답합니다", label, reason(error));
	}

	private T fallbackLocked() {
		// 캐시가 없으면 unavailable(lock 안이지만 짧고, 외부를 부르지 않는다)
		return last != null ? asStale.apply(last) : unavailable.get();
	}

	private String reason(Throwable error) {
		Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
		if (cause instanceof TimeoutException) {
			return "갱신 " + refreshTimeout.toMillis() + "ms 초과";
		}
		if (cause instanceof PrometheusException) {
			return cause.getMessage();
		}
		return cause.getClass().getSimpleName();
	}

}
