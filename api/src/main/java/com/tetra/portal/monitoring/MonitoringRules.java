package com.tetra.portal.monitoring;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.tetra.portal.common.time.Kst;
import com.tetra.portal.domain.EventStatus;
import com.tetra.portal.domain.HostingStatus;
import com.tetra.portal.monitoring.MonitoringReader.EventDetail;
import com.tetra.portal.monitoring.MonitoringReader.EventView;
import com.tetra.portal.monitoring.MonitoringResponses.Service;
import com.tetra.portal.monitoring.MonitoringResponses.Step;
import com.tetra.portal.monitoring.MonitoringResponses.StepKey;
import com.tetra.portal.monitoring.MonitoringResponses.StepStatus;
import com.tetra.portal.result.ResultResponse;

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
	 * 최고 동시 접속 = 1분 칸 값(마지막 칸 = 지금 값) 중 최댓값, 같은 값이면 처음 칸의 분. 값이 하나도 없으면 null.
	 * 칸 값은 그 분에 수집한 게이지라 결과 탭 peak_at(세션을 만든 분)과 같은 분이 된다.
	 */
	static PeakAt peak(List<Long> grid, List<Double> sessions) {
		PeakAt best = null;
		for (int i = 0; i < grid.size(); i++) {
			Long v = count(sessions.get(i));
			if (v != null && (best == null || v > best.value())) {
				best = new PeakAt(v, Instant.ofEpochMilli(grid.get(i)));
			}
		}
		return best;
	}

	/**
	 * 품절 시각 = 남은 재고가 0이 되어 그 뒤로 계속 0인 첫 칸의 분(마지막 칸 = 지금 값). 0 뒤에 다시 양수면(재고를 아직 못 읽은 0 등)
	 * 그 뒤의 0부터 본다. 모르는 칸(null)은 건너뛴다. 없으면 null
	 */
	static Instant soldOutAt(List<Long> grid, List<Double> stock) {
		Instant at = null;
		for (int i = 0; i < grid.size(); i++) {
			Long v = count(stock.get(i));
			if (v == null) {
				continue;
			}
			if (v > 0) {
				at = null;
			}
			else if (at == null) {
				at = Instant.ofEpochMilli(grid.get(i));
			}
		}
		return at;
	}

	/** 호스팅이 시작된 뒤의 상태 — 전이 로그가 빠져도 상태로 완료를 안다 */
	private static final Set<HostingStatus> HOSTING_STARTED = EnumSet.of(HostingStatus.HOSTING, HostingStatus.ENDED,
			HostingStatus.END_FAILED);

	/**
	 * 진행 단계 5개: 호스팅 시작 → 입장 → 이벤트 진행 → 이벤트 종료(end_at) → 결과 집계(2026-10-28 결정).
	 * 완료는 실제 기록(hosting_status · event_status + 첫 전이 로그)으로 정한다 — 시각만으로 완료하지 않는다(이벤트 종료는 end_at 시각 = 발급 마감).
	 * CURRENT는 지금 실제로 진행 중인 단계만 — 앞 단계가 끝났고 그 단계의 시각이 됐을 때. 예정 시각 전 기다리는 구간은 UPCOMING.
	 * 예정 시각이 지났는데 아직이면 delayed(web "확인 중") — 호스팅 시작 · 입장만.
	 * UNAVAILABLE(배포 · 호스팅 시작 실패)에는 부르지 않는다(단계 없음).
	 * - 호스팅 시작: HOSTING 로그 또는 HOSTING · ENDED · END_FAILED면 완료(실제 시각 = 로그). 시각이 지났는데 아직이면 현재 + delayed
	 * - 입장: 입장 시각부터 현재(OPEN이면 입장 중, 아니면 delayed). OPEN이 된 적 있고(OPEN 로그 · 지금 OPEN) 시작 시각이 지났으면 완료.
	 *   OPEN_FAILED이거나 OPEN이 한 번도 안 된 채 종료면 실패. WF4는 어느 상태에서든 EXPIRED로 바꾸므로 EXPIRED는 OPEN의 증거가 아니다
	 * - 이벤트 진행: 입장이 끝나면(OPEN 뒤 시작) 현재, 종료 상태(end_at · 종료 기록)면 완료
	 * - 이벤트 종료: 종료 상태면 완료(진행 중인 구간이 없다 — 그 전에는 예정)
	 * - 결과 집계: 예정 시각 = hosting_end_at. 그 시각이 지났거나 종료 기록(hosting ENDED · event EXPIRED)이 있으면 현재(집계 중),
	 *   결과가 준비되면 완료, END_FAILED면 실패. end_at ~ hosting_end_at은 기다리는 구간(예정)
	 * - OPEN이 한 번도 안 된 채(OPEN_FAILED 포함) 끝난 이벤트: 이벤트 진행 · 이벤트 종료 · 결과 집계는 SKIPPED(진행 안 됨)
	 */
	static List<Step> steps(MonitoringPhase phase, LocalDateTime now, EventDetail detail) {
		EventView e = detail.event();
		LocalDateTime entryAt = MonitoringPhase.entryAt(e.startAt());
		boolean ended = phase == MonitoringPhase.ENDED;
		boolean hosted = detail.hostingStartedAt() != null || HOSTING_STARTED.contains(e.hostingStatus());
		boolean opened = detail.openedAt() != null || e.eventStatus() == EventStatus.OPEN;
		boolean entryFailed = !opened && (e.eventStatus() == EventStatus.OPEN_FAILED || ended);
		boolean endRecorded = e.hostingStatus() == HostingStatus.ENDED || e.eventStatus() == EventStatus.EXPIRED;
		boolean notRun = ended && !opened;
		boolean ready = detail.resultState() == ResultResponse.State.READY;

		boolean[] done = { hosted, opened && !now.isBefore(e.startAt()), opened && ended, ended, ended && ready };
		boolean[] skipped = { false, false, notRun, notRun, notRun };
		boolean[] failed = { false, entryFailed, false, false, detail.resultState() == ResultResponse.State.END_FAILED };
		// 앞 단계가 끝났을 때 이 단계가 진행 중인지 — 예정 시각 전(기다리는 구간)은 아니다
		boolean[] active = { !now.isBefore(e.hostingStartAt()), !now.isBefore(entryAt), true, false,
				endRecorded || !now.isBefore(e.hostingEndAt()) };
		boolean[] delayed = { !hosted && !now.isBefore(e.hostingStartAt()),
				!opened && !entryFailed && !now.isBefore(entryAt), false, false, false };
		LocalDateTime[] at = { e.hostingStartAt(), entryAt, e.startAt(), e.endAt(), e.hostingEndAt() };
		LocalDateTime[] actualAt = { detail.hostingStartedAt(), detail.openedAt(), null, null, null };
		StepKey[] keys = StepKey.values();
		List<Step> steps = new ArrayList<>(keys.length);
		boolean previousDone = true;
		for (int i = 0; i < keys.length; i++) {
			StepStatus status = skipped[i] ? StepStatus.SKIPPED : done[i] ? StepStatus.DONE
					: failed[i] ? StepStatus.FAILED : previousDone && active[i] ? StepStatus.CURRENT : StepStatus.UPCOMING;
			previousDone = status == StepStatus.DONE;
			steps.add(new Step(keys[i], Kst.toOffset(at[i]), Kst.toOffset(actualAt[i]), status, delayed[i]));
		}
		return steps;
	}

}
