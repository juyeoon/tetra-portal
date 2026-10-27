package com.tetra.portal.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tetra.portal.domain.EventStatus;
import com.tetra.portal.domain.HostingStatus;
import com.tetra.portal.monitoring.MonitoringReader.EventDetail;
import com.tetra.portal.monitoring.MonitoringReader.EventView;
import com.tetra.portal.monitoring.MonitoringResponses.Service;
import com.tetra.portal.monitoring.MonitoringResponses.Step;
import com.tetra.portal.monitoring.MonitoringResponses.StepKey;
import com.tetra.portal.monitoring.MonitoringResponses.StepStatus;

/** 서비스 표시 · 예상 대기 · 소진 예상 · 최고 동시 접속 · 품절 시각 · 진행 단계 · null/0 규칙. */
class MonitoringRulesTest {

	static final Instant T = Instant.parse("2026-10-27T06:00:00Z"); // 15:00 KST

	// ---- 서비스 표시 ----

	static Service service(MetricsFixture f) {
		return MonitoringRules.service(f.build(), false);
	}

	@Test
	void 위험_조건이_없으면_정상() {
		assertThat(service(new MetricsFixture())).isEqualTo(Service.NORMAL);
	}

	@Test
	void 오류_비율_2퍼센트부터_지연_중() {
		var f = new MetricsFixture();
		f.errorRps = 1.99;
		assertThat(service(f)).isEqualTo(Service.NORMAL); // 1.99% — Console 주의(1%)는 고객사에 정상
		f.errorRps = 2.0;
		assertThat(service(f)).isEqualTo(Service.DELAYED);
	}

	@Test
	void 발급_p95_2초부터_지연_중() {
		var f = new MetricsFixture();
		f.claimP95 = 1.99;
		assertThat(service(f)).isEqualTo(Service.NORMAL);
		f.claimP95 = 2.0;
		assertThat(service(f)).isEqualTo(Service.DELAYED);
	}

	@Test
	void redis_up_0이면_지연_중_시리즈가_없으면_확인_중() {
		var f = new MetricsFixture();
		f.redisUp = 0.0;
		assertThat(service(f)).isEqualTo(Service.DELAYED);
		f.redisUp = null;
		assertThat(service(f)).isEqualTo(Service.CHECKING);
	}

	@Test
	void 요청이_없으면_오류_비율_0이고_p95가_없어도_정상() {
		var f = new MetricsFixture();
		f.rps = null;
		f.errorRps = null;
		f.claimP95 = null;
		assertThat(service(f)).isEqualTo(Service.NORMAL);
		f.rps = 0.0;
		assertThat(service(f)).isEqualTo(Service.NORMAL);
	}

	@Test
	void 판정_쿼리가_실패하면_확인_중_단_보이는_위험이_먼저() {
		var f = new MetricsFixture();
		f.serviceQueriesOk = false;
		assertThat(service(f)).isEqualTo(Service.CHECKING);
		f.claimP95 = 3.0;
		assertThat(service(f)).isEqualTo(Service.DELAYED);
	}

	@Test
	void 데이터_지연이거나_값이_없으면_확인_중() {
		var f = new MetricsFixture();
		f.redisUp = 0.0;
		assertThat(MonitoringRules.service(f.build(), true)).isEqualTo(Service.CHECKING);
		assertThat(MonitoringRules.service(null, true)).isEqualTo(Service.CHECKING);
	}

	// ---- 예상 대기 ----

	@Test
	void 예상_대기는_대기_인원_나누기_최근_1분_처리_수() {
		assertThat(MonitoringRules.waitMinutes(MonitoringPhase.LIVE, 2400L, 600.0)).isEqualTo(4);
		assertThat(MonitoringRules.waitMinutes(MonitoringPhase.LIVE, 2500L, 1000.0)).isEqualTo(3); // 2.5 → 반올림
		assertThat(MonitoringRules.waitMinutes(MonitoringPhase.LIVE, 300L, 600.0)).isZero(); // 1분 이내
		assertThat(MonitoringRules.waitMinutes(MonitoringPhase.LIVE, 0L, 0.0)).isZero(); // 기다리는 사람 없음
	}

	@Test
	void 처리_수가_0이거나_모르면_예상_대기_없음() {
		assertThat(MonitoringRules.waitMinutes(MonitoringPhase.LIVE, 2400L, 0.0)).isNull();
		assertThat(MonitoringRules.waitMinutes(MonitoringPhase.LIVE, 2400L, null)).isNull();
		assertThat(MonitoringRules.waitMinutes(MonitoringPhase.LIVE, null, 600.0)).isNull();
	}

	@Test
	void 시작_전과_품절_뒤에는_예상_대기_없음() {
		assertThat(MonitoringRules.waitMinutes(MonitoringPhase.ENTRY_OPEN, 0L, 0.0)).isNull();
		assertThat(MonitoringRules.waitMinutes(MonitoringPhase.SOLD_OUT, 2400L, 600.0)).isNull();
	}

	// ---- 소진 예상 ----

	@Test
	void 소진_예상은_남은_재고_나누기_성공_속도() {
		Instant at = T.plusSeconds(720);
		assertThat(MonitoringRules.projection(MonitoringPhase.LIVE, T, at, 5460L, 10.0)).isEqualTo(at.plusSeconds(546));
	}

	@Test
	void 소진_예상은_시작_5분_뒤부터() {
		assertThat(MonitoringRules.projection(MonitoringPhase.LIVE, T, T.plusSeconds(299), 5460L, 10.0)).isNull();
		assertThat(MonitoringRules.projection(MonitoringPhase.LIVE, T, T.plusSeconds(300), 5460L, 10.0)).isNotNull();
	}

	@Test
	void 속도가_0이거나_품절이면_소진_예상_없음() {
		Instant at = T.plusSeconds(600);
		assertThat(MonitoringRules.projection(MonitoringPhase.LIVE, T, at, 5460L, 0.0)).isNull();
		assertThat(MonitoringRules.projection(MonitoringPhase.LIVE, T, at, 5460L, null)).isNull();
		assertThat(MonitoringRules.projection(MonitoringPhase.LIVE, T, at, 0L, 10.0)).isNull();
		assertThat(MonitoringRules.projection(MonitoringPhase.SOLD_OUT, T, at, 0L, 10.0)).isNull();
		assertThat(MonitoringRules.projection(MonitoringPhase.LIVE, T, at, null, 10.0)).isNull();
	}

	// ---- 최고 동시 접속 · 품절 시각 ----

	static List<Long> grid(int minutes) {
		return java.util.stream.IntStream.range(0, minutes).mapToObj(i -> T.plusSeconds(60L * i).toEpochMilli()).toList();
	}

	@Test
	void 최고_동시_접속은_최댓값과_처음_나온_시각() {
		var peak = MonitoringRules.peak(grid(4), Arrays.asList(10.0, null, 30.0, 30.0), 20.0, T.plusSeconds(250));
		assertThat(peak).isEqualTo(new MonitoringRules.PeakAt(30, T.plusSeconds(120)));
	}

	@Test
	void 지금_값이_격자보다_크면_지금() {
		var at = T.plusSeconds(200);
		assertThat(MonitoringRules.peak(grid(2), List.of(10.0, 20.0), 25.0, at)).isEqualTo(new MonitoringRules.PeakAt(25, at));
	}

	@Test
	void 값이_하나도_없으면_최고_동시_접속_없음() {
		assertThat(MonitoringRules.peak(grid(2), Arrays.asList(null, null), null, T)).isNull();
		assertThat(MonitoringRules.peak(List.of(), List.of(), null, T)).isNull();
	}

	@Test
	void 품절_시각은_재고가_처음_0이_된_분() {
		assertThat(MonitoringRules.soldOutAt(grid(4), Arrays.asList(5.0, null, 0.0, 0.0), 0L, T.plusSeconds(250)))
			.isEqualTo(T.plusSeconds(120));
	}

	@Test
	void 추이에_아직_없으면_지금_분() {
		assertThat(MonitoringRules.soldOutAt(grid(2), List.of(5.0, 1.0), 0L, T.plusSeconds(150))).isEqualTo(T.plusSeconds(120));
		assertThat(MonitoringRules.soldOutAt(grid(2), List.of(5.0, 1.0), 1L, T.plusSeconds(150))).isNull();
	}

	// ---- null / 0 ----

	@Test
	void 개수는_반올림하고_0_미만은_0_null은_null() {
		assertThat(MonitoringRules.count(2.5)).isEqualTo(3);
		assertThat(MonitoringRules.count(0.0)).isZero();
		assertThat(MonitoringRules.count(-12.0)).isZero(); // 대기 = 번호표 − 커서가 음수(커서가 앞섬)
		assertThat(MonitoringRules.count(null)).isNull();
	}

	// ---- 진행 단계 ----

	static final LocalDateTime START = LocalDateTime.of(2026, 10, 27, 15, 0);

	static final LocalDateTime HOSTING_START = START.minusMinutes(100);

	static final LocalDateTime ENTRY = MonitoringPhase.entryAt(START);

	static final LocalDateTime HOSTING_END = START.plusMinutes(40);

	static final LocalDateTime HOSTED_AT = HOSTING_START.plusMinutes(1);

	static final LocalDateTime OPENED_AT = ENTRY.plusSeconds(10);

	/** 상태 · 첫 전이 로그 시각(HOSTING · OPEN, 없으면 null) */
	static EventDetail detail(HostingStatus hosting, EventStatus event, LocalDateTime hostingStartedAt,
			LocalDateTime openedAt, boolean resultReady) {
		var view = new EventView(1L, "이벤트", HOSTING_START, START, HOSTING_END, HOSTING_END, event, hosting, 15000L, "M");
		return new EventDetail(view, 30000L, resultReady, null, hostingStartedAt, openedAt);
	}

	static List<Step> stepsOf(MonitoringPhase phase, LocalDateTime now, EventDetail detail) {
		return MonitoringRules.steps(phase, now, detail);
	}

	static List<StepStatus> statuses(MonitoringPhase phase, LocalDateTime now, EventDetail detail) {
		return stepsOf(phase, now, detail).stream().map(Step::status).toList();
	}

	static List<Boolean> delays(MonitoringPhase phase, LocalDateTime now, EventDetail detail) {
		return stepsOf(phase, now, detail).stream().map(Step::delayed).toList();
	}

	static final StepStatus D = StepStatus.DONE;

	static final StepStatus C = StepStatus.CURRENT;

	static final StepStatus U = StepStatus.UPCOMING;

	static final StepStatus F = StepStatus.FAILED;

	static final StepStatus S = StepStatus.SKIPPED;

	static final EventDetail DEPLOYED = detail(HostingStatus.DEPLOYED, EventStatus.SCHEDULED, null, null, false);

	static final EventDetail HOSTED = detail(HostingStatus.HOSTING, EventStatus.SCHEDULED, HOSTED_AT, null, false);

	static final EventDetail OPENED = detail(HostingStatus.HOSTING, EventStatus.OPEN, HOSTED_AT, OPENED_AT, false);

	@Test
	void 호스팅_시작_전에는_호스팅_시작이_현재() {
		var steps = stepsOf(MonitoringPhase.BEFORE_ENTRY, HOSTING_START.minusMinutes(1), DEPLOYED);
		assertThat(steps).extracting(Step::status).containsExactly(C, U, U, U, U);
		assertThat(steps).extracting(Step::delayed).containsOnly(false);
		assertThat(steps.get(0).actualAt()).isNull();
	}

	@Test
	void 호스팅_시작_시각이_지났는데_DEPLOYED면_현재_그대로_지연() {
		// 시각만으로 완료하지 않는다 — WF2-1이 확인 중이거나 돌지 않았다
		assertThat(statuses(MonitoringPhase.BEFORE_ENTRY, HOSTING_START, DEPLOYED)).containsExactly(C, U, U, U, U);
		assertThat(delays(MonitoringPhase.BEFORE_ENTRY, HOSTING_START, DEPLOYED)).containsExactly(true, false, false, false,
				false);
		assertThat(statuses(MonitoringPhase.ENTRY_OPEN, ENTRY, DEPLOYED)).containsExactly(C, U, U, U, U);
	}

	@Test
	void HOSTING이면_완료_시각은_첫_전이_로그() {
		var steps = stepsOf(MonitoringPhase.BEFORE_ENTRY, HOSTING_START.plusMinutes(2), HOSTED);
		assertThat(steps).extracting(Step::status).containsExactly(D, C, U, U, U);
		assertThat(steps.get(0).actualAt().toLocalDateTime()).isEqualTo(HOSTED_AT);
		assertThat(steps.get(0).delayed()).isFalse();
	}

	@Test
	void 로그가_없어도_HOSTING_뒤_상태면_호스팅_시작은_완료() {
		for (HostingStatus status : List.of(HostingStatus.HOSTING, HostingStatus.ENDED, HostingStatus.END_FAILED)) {
			var steps = stepsOf(MonitoringPhase.BEFORE_ENTRY, START.minusMinutes(90),
					detail(status, EventStatus.SCHEDULED, null, null, false));
			assertThat(steps.get(0).status()).as(status.name()).isEqualTo(D);
			assertThat(steps.get(0).actualAt()).isNull(); // 화면은 예정 시각으로
		}
	}

	@Test
	void 입장_전에는_입장이_현재() {
		assertThat(statuses(MonitoringPhase.BEFORE_ENTRY, ENTRY.minusMinutes(1), HOSTED)).containsExactly(D, C, U, U, U);
		assertThat(delays(MonitoringPhase.BEFORE_ENTRY, ENTRY.minusMinutes(1), HOSTED)).containsOnly(false);
	}

	@Test
	void 입장_시각이_지났는데_OPEN이_아니면_입장이_현재_그대로_지연() {
		assertThat(statuses(MonitoringPhase.ENTRY_OPEN, ENTRY, HOSTED)).containsExactly(D, C, U, U, U);
		assertThat(delays(MonitoringPhase.ENTRY_OPEN, ENTRY, HOSTED)).containsExactly(false, true, false, false, false);
		// 시작 시각이 지나도 OPEN이 안 됐으면 이벤트 진행은 현재가 되지 않는다
		assertThat(statuses(MonitoringPhase.LIVE, START.plusMinutes(1), HOSTED)).containsExactly(D, C, U, U, U);
		assertThat(delays(MonitoringPhase.LIVE, START.plusMinutes(1), HOSTED).get(1)).isTrue();
	}

	@Test
	void 입장_이후_시작_전에는_입장이_현재_시작하면_이벤트_진행이_현재() {
		var entry = stepsOf(MonitoringPhase.ENTRY_OPEN, ENTRY.plusSeconds(30), OPENED);
		assertThat(entry).extracting(Step::status).containsExactly(D, C, U, U, U);
		assertThat(entry.get(1).delayed()).isFalse();
		assertThat(entry.get(1).actualAt().toLocalDateTime()).isEqualTo(OPENED_AT);
		assertThat(statuses(MonitoringPhase.LIVE, START, OPENED)).containsExactly(D, D, C, U, U);
		assertThat(statuses(MonitoringPhase.SOLD_OUT, START.plusMinutes(10), OPENED)).containsExactly(D, D, C, U, U);
	}

	@Test
	void OPEN_FAILED면_입장은_실패_입장_시각_전후_모두() {
		var failed = detail(HostingStatus.HOSTING, EventStatus.OPEN_FAILED, HOSTED_AT, null, false);
		// 준비 실패는 종료 전까지 BEFORE_ENTRY
		for (LocalDateTime now : List.of(ENTRY.minusMinutes(1), ENTRY, START.plusMinutes(1))) {
			assertThat(statuses(MonitoringPhase.BEFORE_ENTRY, now, failed)).as(now.toString()).containsExactly(D, F, U, U, U);
			assertThat(delays(MonitoringPhase.BEFORE_ENTRY, now, failed)).containsOnly(false);
		}
	}

	@Test
	void 종료_전에는_종료_칸이_지연이_아니다() {
		assertThat(delays(MonitoringPhase.LIVE, HOSTING_END.minusSeconds(1), OPENED)).containsOnly(false);
	}

	@Test
	void 정상_이벤트는_종료_시각이_지나도_WF4가_끝날_때까지_종료_처리_중() {
		// hosting_end_at — 상태는 시각으로 ENDED지만 아직 HOSTING · OPEN. 이벤트 진행은 완료, 종료는 현재 + 지연(종료 처리 중)
		var steps = stepsOf(MonitoringPhase.ENDED, HOSTING_END, OPENED);
		assertThat(steps).extracting(Step::status).containsExactly(D, D, D, C, U);
		assertThat(steps).extracting(Step::delayed).containsExactly(false, false, false, true, false);
		// WF4 성공(ENDED · EXPIRED) — 종료 완료, 결과 집계가 현재(집계 중) → 결과가 준비되면 완료
		var wf4 = detail(HostingStatus.ENDED, EventStatus.EXPIRED, HOSTED_AT, OPENED_AT, false);
		assertThat(statuses(MonitoringPhase.ENDED, HOSTING_END.plusMinutes(5), wf4)).containsExactly(D, D, D, D, C);
		assertThat(delays(MonitoringPhase.ENDED, HOSTING_END.plusMinutes(5), wf4)).containsOnly(false);
		var ready = detail(HostingStatus.ENDED, EventStatus.EXPIRED, HOSTED_AT, OPENED_AT, true);
		assertThat(statuses(MonitoringPhase.ENDED, HOSTING_END.plusMinutes(30), ready)).containsExactly(D, D, D, D, D);
	}

	@Test
	void END_FAILED면_종료는_현재_종료_처리_중() {
		var endFailed = detail(HostingStatus.END_FAILED, EventStatus.OPEN, HOSTED_AT, OPENED_AT, false);
		assertThat(statuses(MonitoringPhase.ENDED, HOSTING_END.plusMinutes(5), endFailed)).containsExactly(D, D, D, C, U);
		assertThat(delays(MonitoringPhase.ENDED, HOSTING_END.plusMinutes(5), endFailed).get(3)).isTrue();
	}

	@Test
	void OPEN이_한_번도_안_된_채_끝나면_입장은_실패_뒤_단계는_진행_안_됨() {
		// OPEN_FAILED인 채 종료 시각 · WF4가 EXPIRED로 바꾼 뒤(EXPIRED는 OPEN의 증거가 아니다) · 결과 행이 있어도 · 준비가 아예 안 돈 경우
		var cases = List.of(detail(HostingStatus.HOSTING, EventStatus.OPEN_FAILED, HOSTED_AT, null, false),
				detail(HostingStatus.ENDED, EventStatus.EXPIRED, HOSTED_AT, null, false),
				detail(HostingStatus.ENDED, EventStatus.EXPIRED, HOSTED_AT, null, true),
				detail(HostingStatus.HOSTING, EventStatus.SCHEDULED, HOSTED_AT, null, false));
		for (EventDetail d : cases) {
			assertThat(statuses(MonitoringPhase.ENDED, HOSTING_END.plusMinutes(5), d)).containsExactly(D, F, S, S, S);
			assertThat(delays(MonitoringPhase.ENDED, HOSTING_END.plusMinutes(5), d)).containsOnly(false);
		}
	}

	@Test
	void 단계_시각은_호스팅_시작_입장_시작_종료_그리고_집계는_없음() {
		var steps = stepsOf(MonitoringPhase.LIVE, START, OPENED);
		assertThat(steps).extracting(Step::step).containsExactly(StepKey.values());
		assertThat(steps).extracting(s -> s.at() == null ? null : s.at().toLocalDateTime())
			.containsExactly(HOSTING_START, ENTRY, START, HOSTING_END, null);
		// 실제 시각은 호스팅 시작 · 입장만
		assertThat(steps).extracting(s -> s.actualAt() == null ? null : s.actualAt().toLocalDateTime())
			.containsExactly(HOSTED_AT, OPENED_AT, null, null, null);
	}

}
