package com.tetra.portal.monitoring;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 시험용 {@link Metrics}. 기본은 정상 진행 중(Redis up, 요청 있음, 5xx 0, p95 0.1초, 격자 없음). */
final class MetricsFixture {

	Instant at = Instant.parse("2026-10-27T06:10:00Z");

	Double stockRemaining = 5460.0;

	Double waiting = 2400.0;

	Double sessionsActive = 6820.0;

	Double redisUp = 1.0;

	Double soldOutTotal = 0.0;

	Double processedLastMinute = 600.0;

	Double successRate5m = 10.0;

	Double rejects5m = 0.0;

	Double rps = 100.0;

	Double errorRps = 0.0;

	Double claimP95 = 0.1;

	boolean serviceQueriesOk = true;

	List<Long> grid = new ArrayList<>();

	List<Double> entries = new ArrayList<>();

	List<Double> success = new ArrayList<>();

	List<Double> soldOut = new ArrayList<>();

	List<Double> stock = new ArrayList<>();

	List<Double> sessions = new ArrayList<>();

	/** 격자에 한 분 더한다 */
	MetricsFixture minute(Instant t, Double entries, Double success, Double soldOut, Double stock, Double sessions) {
		grid.add(t.toEpochMilli());
		this.entries.add(entries);
		this.success.add(success);
		this.soldOut.add(soldOut);
		this.stock.add(stock);
		this.sessions.add(sessions);
		return this;
	}

	Metrics build() {
		return new Metrics(at, stockRemaining, waiting, sessionsActive, redisUp, soldOutTotal, processedLastMinute,
				successRate5m, rejects5m, rps, errorRps, claimP95, serviceQueriesOk, grid, entries, success, soldOut, stock,
				sessions);
	}

}
