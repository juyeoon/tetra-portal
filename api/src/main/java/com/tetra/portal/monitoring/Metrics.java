package com.tetra.portal.monitoring;

import java.time.Instant;
import java.util.List;

/**
 * Prometheus 조회 1회 결과 — 캐시에는 이것만 넣는다(상태 · 남은 시간 · 진행 단계는 요청마다 현재 시각으로).
 * 게이지 · 비율 값은 Prometheus 원본 그대로다. null = 시리즈 없음 · NaN · 그 쿼리만 실패. 반올림 · 0 하한 · 파생 값은 {@link MonitoringRules}가 만든다.
 * 카운터 칸(입장 · 발급 성공 · 품절)과 품절 응답 누적은 Pod별 누적값 차이로 만든 정수({@link PromValues#perMinute}).
 *
 * @param at 조회 기준 시각(응답 lastUpdatedAt, 소진 예상의 기준)
 * @param soldOutTotal 품절 응답 — 입장부터 지금까지(품절 칸의 합)
 * @param serviceQueriesOk 서비스 판정 쿼리(rps · errorRps · p95 · redis_up)가 모두 성공했는지
 * @param grid 1분 칸의 시작 시각(epoch 밀리초, 입장 분 ~ 지금 분). 아래 목록은 모두 이 격자와 같은 길이 —
 *        카운터는 그 칸의 증가분(마지막 칸은 지금까지), 게이지는 그 칸 분에 읽은 값(마지막 칸은 지금 값)
 */
record Metrics(Instant at,
		Double stockRemaining, Double waiting, Double sessionsActive, Double redisUp,
		Long soldOutTotal, Double processedLastMinute, Double successRate5m, Double rejects5m,
		Double rps, Double errorRps, Double claimP95, boolean serviceQueriesOk,
		List<Long> grid, List<Long> entries, List<Long> success, List<Long> soldOut, List<Double> stock,
		List<Double> sessions) {
}
