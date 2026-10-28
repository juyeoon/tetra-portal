package com.tetra.portal.monitoring;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.stereotype.Component;

import com.tetra.portal.common.time.Kst;
import com.tetra.portal.monitoring.PromBatch.Results;
import com.tetra.portal.monitoring.PromValues.Grid;
import com.tetra.portal.monitoring.PrometheusClient.PrometheusException;

/**
 * 이벤트 하나의 Prometheus 갱신 1회(명세 3절) — instant 12개 + range 4개를 {@link PromBatch}로 4개씩 병렬.
 * 범위는 입장 시각 ~ 지금(1분 격자). 칸 시각은 1분 구간의 시작이고 마지막 칸은 지금 값까지({@link PromValues}).
 * Prometheus 자체 문제면 {@link PrometheusException}(→ 캐시가 마지막 값을 stale로), 쿼리 하나의 오류면 그 값만 null
 * (카운터는 범위 · 지금 값 중 하나라도 실패하면 그 칸들과 품절 응답 누적 모두 null).
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
			.instant("processedLastMinute", q.processedLastMinute(), now)
			.instant("successRate5m", q.successRate5m(), now)
			.instant("rejects5m", q.rejects5m(), now)
			.instant("rps", q.rps(), now)
			.instant("errorRps", q.errorRps(), now)
			.instant("claimP95", q.claimP95(), now)
			.instant("claimsNow", q.claimsByPod(), now)
			.instant("entriesNow", q.entriesByPod(), now)
			.range("claims", q.claimsByPod(), grid.start(), grid.end(), STEP)
			.range("entries", q.entriesByPod(), grid.start(), grid.end(), STEP)
			.range("stock", q.stockRemaining(), grid.start(), grid.end(), STEP)
			.range("sessions", q.sessionsActive(), grid.start(), grid.end(), STEP)
			.execute();
		List<Long> times = grid.timestamps();
		Map<String, List<Long>> claims = counter(r, "claims", "claimsNow", times, "result");
		List<Long> soldOut = claims.getOrDefault("sold_out", nulls(times));
		return new Metrics(now, single(r, "stockRemaining"), single(r, "waiting"), single(r, "sessionsActive"),
				single(r, "redisUp"), PromValues.total(soldOut), single(r, "processedLastMinute"),
				single(r, "successRate5m"), single(r, "rejects5m"), single(r, "rps"), single(r, "errorRps"),
				single(r, "claimP95"), r.allOk(SERVICE_QUERIES.toArray(String[]::new)), times,
				counter(r, "entries", "entriesNow", times, null).getOrDefault("", nulls(times)),
				claims.getOrDefault("success", nulls(times)), soldOut,
				PromValues.gaugeByMinute(r.range("stock"), single(r, "stockRemaining"), times),
				PromValues.gaugeByMinute(r.range("sessions"), single(r, "sessionsActive"), times));
	}

	private static Double single(Results r, String name) {
		return PromValues.single(r.instant(name));
	}

	/** 범위 · 지금 값이 둘 다 있어야 차이를 낸다 — 하나만 있으면 누적 전체가 한 칸에 몰린다 */
	private static Map<String, List<Long>> counter(Results r, String range, String now, List<Long> grid, String group) {
		if (!r.ok(range) || !r.ok(now)) {
			return Map.of();
		}
		return PromValues.perMinute(r.range(range), r.instant(now), grid, group);
	}

	private static List<Long> nulls(List<Long> grid) {
		return Collections.nCopies(grid.size(), null);
	}

}
