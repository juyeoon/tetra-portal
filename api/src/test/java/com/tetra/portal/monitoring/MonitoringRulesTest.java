package com.tetra.portal.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

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

	static List<StepStatus> steps(MonitoringPhase phase, LocalDateTime now, boolean resultReady) {
		return MonitoringRules.steps(phase, now, START.minusMinutes(100), START.minusMinutes(60), START,
				START.plusMinutes(40), resultReady).stream().map(Step::status).toList();
	}

	static final StepStatus D = StepStatus.DONE;

	static final StepStatus C = StepStatus.CURRENT;

	static final StepStatus U = StepStatus.UPCOMING;

	@Test
	void 완료되지_않은_첫_단계가_현재_호스팅_시작은_지나면_완료() {
		// 호스팅 시작 = START − 100분. 그전에는 호스팅 시작이 현재
		assertThat(steps(MonitoringPhase.BEFORE_ENTRY, START.minusMinutes(101), false)).containsExactly(C, U, U, U, U);
		assertThat(steps(MonitoringPhase.BEFORE_ENTRY, START.minusMinutes(100), false)).containsExactly(D, C, U, U, U);
	}

	@Test
	void 입장_전에는_입장이_현재() {
		assertThat(steps(MonitoringPhase.BEFORE_ENTRY, START.minusMinutes(90), false)).containsExactly(D, C, U, U, U);
		assertThat(steps(MonitoringPhase.ENTRY_OPEN, START.minusMinutes(30), false)).containsExactly(D, C, U, U, U);
	}

	@Test
	void 준비_실패도_입장_전과_같다_입장_시각이_지나도() {
		// 준비 실패는 종료 전까지 BEFORE_ENTRY — 입장(START − 60분)이 지나도 입장이 현재
		assertThat(steps(MonitoringPhase.BEFORE_ENTRY, START.minusMinutes(40), false)).containsExactly(D, C, U, U, U);
	}

	@Test
	void 진행_중_품절_종료() {
		assertThat(steps(MonitoringPhase.LIVE, START.plusMinutes(10), false)).containsExactly(D, D, C, U, U);
		assertThat(steps(MonitoringPhase.SOLD_OUT, START.plusMinutes(10), false)).containsExactly(D, D, C, U, U);
		assertThat(steps(MonitoringPhase.ENDED, START.plusMinutes(50), false)).containsExactly(D, D, D, D, C); // 집계 중
		assertThat(steps(MonitoringPhase.ENDED, START.plusMinutes(50), true)).containsExactly(D, D, D, D, D); // 완료
	}

	@Test
	void 단계_시각은_호스팅_시작_입장_시작_종료_그리고_집계는_없음() {
		var steps = MonitoringRules.steps(MonitoringPhase.LIVE, START, START.minusMinutes(100), START.minusMinutes(60), START,
				START.plusMinutes(40), false);
		assertThat(steps).extracting(Step::step).containsExactly(StepKey.values());
		assertThat(steps).extracting(s -> s.at() == null ? null : s.at().toLocalDateTime())
			.containsExactly(START.minusMinutes(100), START.minusMinutes(60), START, START.plusMinutes(40), null);
	}

}
