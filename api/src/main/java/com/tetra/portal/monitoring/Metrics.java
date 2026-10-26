package com.tetra.portal.monitoring;

import java.time.Instant;
import java.util.List;

/**
 * Prometheus 조회 1회 결과 — 캐시에는 이것만 넣는다(상태 · 남은 시간 · 진행 단계는 요청마다 현재 시각으로).
 * 값은 Prometheus 원본 그대로다. null = 시리즈 없음 · NaN · 그 쿼리만 실패. 반올림 · 0 하한 · 파생 값은 {@link MonitoringRules}가 만든다.
 *
 * @param at 조회 기준 시각(응답 lastUpdatedAt, 소진 예상의 기준)
 * @param serviceQueriesOk 서비스 판정 쿼리(rps · errorRps · p95 · redis_up)가 모두 성공했는지
 * @param grid 범위 쿼리 시각(epoch 밀리초, 1분 간격). 아래 목록은 모두 이 격자와 같은 길이 — 값은 그 시각까지 1분(또는 그 시각의 게이지)
 */
record Metrics(Instant at,
		Double stockRemaining, Double waiting, Double sessionsActive, Double redisUp,
		Double soldOutTotal, Double processedLastMinute, Double successRate5m, Double rejects5m,
		Double rps, Double errorRps, Double claimP95, boolean serviceQueriesOk,
		List<Long> grid, List<Double> entries, List<Double> success, List<Double> soldOut, List<Double> stock,
		List<Double> sessions) {
}
