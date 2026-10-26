package com.tetra.portal.monitoring;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.tetra.portal.common.time.Kst;
import com.tetra.portal.monitoring.MonitoringResponses.Service;
import com.tetra.portal.monitoring.MonitoringResponses.Step;
import com.tetra.portal.monitoring.MonitoringResponses.StepKey;
import com.tetra.portal.monitoring.MonitoringResponses.StepStatus;

/** 판정 · 계산(명세 2 · 3절). 입력은 Prometheus 원본 값(null 가능), 시각은 호출한 쪽이 준다. */
final class MonitoringRules {

	/** Console TrafficRules ERROR_RATIO_DANGER와 같은 값(Console 화면 상수와 함께 세 곳). 위험 기준만 쓴다 — 주의는 고객사에 "정상" */
	static final double ERROR_RATIO_DANGER = 0.02;

	/** Console TrafficRules P95_DANGER_SECONDS와 같은 값 */
	static final double P95_DANGER_SECONDS = 2.0;

	/** 소진 예상은 시작 5분 뒤부터(최근 5분 속도가 시작 전 0으로 희석되지 않게) */
	static final Duration PROJECTION_AFTER_START = Duration.ofMinutes(5);

	private MonitoringRules() {
	}

	/**
	 * 서비스 표시. stale이거나 값이 없으면 확인 중. 보이는 위험(redis_up 0 · 5xx 2% · p95 2초)이 있으면 지연 중 —
	 * 재료 일부가 비었어도 위험이 보이면 지연 중(Console TrafficRules.event와 같은 순서). 요청이 없으면 5xx 비율 0, p95 없음은 판정에서 뺀다.
	 */
	static Service service(Metrics m, boolean stale) {
		if (stale || m == null) {
			return Service.CHECKING;
		}
		Double ratio = errorRatio(m.rps(), m.errorRps());
		if ((m.redisUp() != null && m.redisUp() == 0) || (ratio != null && ratio >= ERROR_RATIO_DANGER)
				|| (m.claimP95() != null && m.claimP95() >= P95_DANGER_SECONDS)) {
			return Service.DELAYED;
		}
		if (!m.serviceQueriesOk() || m.redisUp() == null) {
			return Service.CHECKING;
		}
		return Service.NORMAL;
	}

	/** 5xx / 전체. 요청 0(또는 시리즈 없음)이면 0 */
	static Double errorRatio(Double rps, Double errorRps) {
		if (rps == null || rps == 0) {
			return 0.0;
		}
		return (errorRps == null ? 0 : errorRps) / rps;
	}

	/** 개수 반올림, 0 미만은 0 */
	static Long count(Double v) {
		return v == null ? null : Math.max(0, Math.round(v));
	}

	/**
	 * 예상 대기(분) = 대기 인원 ÷ 최근 1분 처리 수. 진행 중(LIVE)에만 — 시작 전 · 품절 뒤는 null(화면이 상태로 문구를 고른다).
	 * 대기 0이면 0, 처리 수를 모르거나 0이면 null, 1분 미만이면 0("1분 이내"), 그 밖 반올림.
	 */
	static Integer waitMinutes(MonitoringPhase phase, Long waiting, Double processedLastMinute) {
		if (phase != MonitoringPhase.LIVE || waiting == null) {
			return null;
		}
		if (waiting == 0) {
			return 0;
		}
		if (processedLastMinute == null || processedLastMinute <= 0) {
			return null;
		}
		double minutes = waiting / processedLastMinute;
		return minutes < 1 ? 0 : (int) Math.round(minutes);
	}

	/** 소진 예상 시각 = 조회 시각 + 남은 재고 ÷ 최근 5분 성공 속도. 진행 중 · 시작 5분 뒤 · 재고 &gt; 0 · 속도 &gt; 0일 때만 */
	static Instant projection(MonitoringPhase phase, Instant startAt, Instant at, Long remaining, Double successPerSecond) {
		if (phase != MonitoringPhase.LIVE || at.isBefore(startAt.plus(PROJECTION_AFTER_START)) || remaining == null
				|| remaining <= 0 || successPerSecond == null || successPerSecond <= 0) {
			return null;
		}
		return at.plusSeconds(Math.round(remaining / successPerSecond));
	}

	record PeakAt(long value, Instant at) {
	}

	/**
	 * 최고 동시 접속 = 범위(1분 격자) 값과 지금 값 중 최댓값, 같은 값이면 처음 시각. 값이 하나도 없으면 null.
	 * 격자 끝은 분 단위로 내려 맞춰 지금 값이 더 클 수 있다.
	 */
	static PeakAt peak(List<Long> grid, List<Double> sessions, Double current, Instant at) {
		PeakAt best = null;
		for (int i = 0; i < grid.size(); i++) {
			Long v = count(sessions.get(i));
			if (v != null && (best == null || v > best.value())) {
				best = new PeakAt(v, Instant.ofEpochMilli(grid.get(i)));
			}
		}
		Long now = count(current);
		if (now != null && (best == null || now > best.value())) {
			best = new PeakAt(now, at);
		}
		return best;
	}

	/** 품절 시각 = 남은 재고 추이에서 처음 0이 된 분. 추이에 아직 없고 지금 0이면 지금(분 단위) */
	static Instant soldOutAt(List<Long> grid, List<Double> stock, Long remainingNow, Instant at) {
		for (int i = 0; i < grid.size(); i++) {
			Long v = count(stock.get(i));
			if (v != null && v == 0) {
				return Instant.ofEpochMilli(grid.get(i));
			}
		}
		return remainingNow != null && remainingNow == 0 ? at.truncatedTo(ChronoUnit.MINUTES) : null;
	}

	/**
	 * 진행 단계 5개. 그 단계에 들어섰으면(도착) 가장 늦게 도착한 단계가 CURRENT, 앞은 DONE, 뒤는 UPCOMING.
	 * 결과 집계는 종료와 함께 도착하고, 결과가 준비되면 DONE. 준비 실패(입장 전처럼 보임)는 입장에 도착하지 않는다.
	 */
	static List<Step> steps(MonitoringPhase phase, LocalDateTime now, LocalDateTime hostingStartAt,
			LocalDateTime entryAt, LocalDateTime startAt, LocalDateTime hostingEndAt, boolean resultReady) {
		boolean ended = phase == MonitoringPhase.ENDED;
		boolean live = ended || phase == MonitoringPhase.LIVE || phase == MonitoringPhase.SOLD_OUT;
		boolean entry = live || phase == MonitoringPhase.ENTRY_OPEN;
		boolean hosting = entry || !now.isBefore(hostingStartAt);
		boolean[] arrived = { hosting, entry, live, ended, ended };
		LocalDateTime[] at = { hostingStartAt, entryAt, startAt, hostingEndAt, null };
		StepKey[] keys = StepKey.values();
		int current = -1;
		for (int i = 0; i < arrived.length; i++) {
			if (arrived[i]) {
				current = i;
			}
		}
		List<Step> steps = new ArrayList<>(keys.length);
		for (int i = 0; i < keys.length; i++) {
			StepStatus status = i < current ? StepStatus.DONE : i == current ? StepStatus.CURRENT : StepStatus.UPCOMING;
			if (keys[i] == StepKey.AGGREGATE && ended && resultReady) {
				status = StepStatus.DONE;
			}
			steps.add(new Step(keys[i], Kst.toOffset(at[i]), status));
		}
		return steps;
	}

}
