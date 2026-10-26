package com.tetra.portal.monitoring;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.stereotype.Component;

import com.tetra.portal.common.time.Kst;
import com.tetra.portal.monitoring.PromBatch.Results;
import com.tetra.portal.monitoring.PromValues.Grid;
import com.tetra.portal.monitoring.PrometheusClient.PrometheusException;

/**
 * 이벤트 하나의 Prometheus 갱신 1회(명세 3절) — instant 11개 + range 4개를 {@link PromBatch}로 4개씩 병렬.
 * 범위는 입장 시각 ~ 지금(1분 격자). Prometheus 자체 문제면 {@link PrometheusException}(→ 캐시가 마지막 값을 stale로),
 * 쿼리 하나의 오류면 그 값만 null.
 */
@Component
class MonitoringCollector {

	static final Duration STEP = Duration.ofMinutes(1);

	/** 서비스 판정 재료 — 하나라도 실패하면 "확인 중" */
	private static final List<String> SERVICE_QUERIES = List.of("rps", "errorRps", "claimP95", "redisUp");

	private final PrometheusClient prometheus;

	private final Clock clock;

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	MonitoringCollector(PrometheusClient prometheus, Clock clock) {
		this.prometheus = prometheus;
		this.clock = clock;
	}

	/** @param entryAt 입장 시각(KST) — 범위 쿼리 시작 · 품절 응답 누적 시작 */
	Metrics collect(String tenantId, long eventId, LocalDateTime entryAt, long deadlineNanos) {
		if (!prometheus.configured()) {
			throw new PrometheusException("PROMETHEUS_URL 미설정");
		}
		// 응답의 다른 시각처럼 초 단위(lastUpdatedAt)
		Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
		Instant entry = entryAt.atZone(Kst.ZONE).toInstant();
		MonitoringQueries q = new MonitoringQueries(tenantId, eventId);
		Grid grid = Grid.of(entry, now, STEP);
		Results r = new PromBatch(prometheus, executor, deadlineNanos)
			.instant("stockRemaining", q.stockRemaining(), now)
			.instant("waiting", q.waiting(), now)
			.instant("sessionsActive", q.sessionsActive(), now)
			.instant("redisUp", q.redisUp(), now)
			.instant("soldOutTotal", q.soldOutSince(Duration.between(entry, now)), now)
			.instant("processedLastMinute", q.processedLastMinute(), now)
			.instant("successRate5m", q.successRate5m(), now)
			.instant("rejects5m", q.rejects5m(), now)
			.instant("rps", q.rps(), now)
			.instant("errorRps", q.errorRps(), now)
			.instant("claimP95", q.claimP95(), now)
			.range("entries", q.entriesPerMinute(), grid.start(), grid.end(), STEP)
			.range("claims", q.claimsPerMinute(), grid.start(), grid.end(), STEP)
			.range("stock", q.stockRemaining(), grid.start(), grid.end(), STEP)
			.range("sessions", q.sessionsActive(), grid.start(), grid.end(), STEP)
			.execute();
		List<Long> times = grid.timestamps();
		return new Metrics(now, single(r, "stockRemaining"), single(r, "waiting"), single(r, "sessionsActive"),
				single(r, "redisUp"), single(r, "soldOutTotal"), single(r, "processedLastMinute"),
				single(r, "successRate5m"), single(r, "rejects5m"), single(r, "rps"), single(r, "errorRps"),
				single(r, "claimP95"), r.allOk(SERVICE_QUERIES.toArray(String[]::new)), times,
				PromValues.onGrid(r.range("entries"), times, false),
				PromValues.onGrid(r.range("claims"), "result", "success", times),
				PromValues.onGrid(r.range("claims"), "result", "sold_out", times),
				PromValues.onGrid(r.range("stock"), times, false), PromValues.onGrid(r.range("sessions"), times, false));
	}

	private static Double single(Results r, String name) {
		return PromValues.single(r.instant(name));
	}

}
