package com.tetra.portal.event.apply;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** 신청 일시 규칙 경계 (Console 3-13) — 호스팅 시작 ≤ 이벤트 시작 − 100분, 호스팅 시작 ≥ 지금 + 30분. */
class ApplyPeriodRulesTest {

	static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 9, 0);

	static final LocalDateTime START = NOW.plusDays(1);

	static Map<String, String> errors(LocalDateTime hostingStartAt, LocalDateTime startAt) {
		Map<String, String> errors = new LinkedHashMap<>();
		ApplyService.checkPeriods(startAt, startAt.plusHours(2), hostingStartAt, startAt.plusHours(4), NOW, errors);
		return errors;
	}

	@Test
	void hostingStartMustBeAtLeast100MinutesBeforeEventStart() {
		// TEST-LEAD 원래: assertThat(errors(START.minusMinutes(99), START))
		assertThat(errors(START.minusMinutes(34), START)) // TEST-LEAD
			// TEST-LEAD 원래: .containsEntry("hostingStartAt", "호스팅 시작은 이벤트 시작 100분 전까지여야 합니다.");
			.containsEntry("hostingStartAt", "호스팅 시작은 이벤트 시작 35분 전까지여야 합니다."); // TEST-LEAD
		// TEST-LEAD 원래: assertThat(errors(START.minusMinutes(100), START)).isEmpty();
		assertThat(errors(START.minusMinutes(35), START)).isEmpty(); // TEST-LEAD
		// TEST-LEAD 원래: assertThat(errors(START.minusMinutes(101), START)).isEmpty();
		assertThat(errors(START.minusMinutes(36), START)).isEmpty(); // TEST-LEAD
		assertThat(errors(START, START)).containsKey("hostingStartAt");
	}

	@Test
	void hostingStartMustBeAtLeast30MinutesFromNow() {
		LocalDateTime farStart = NOW.plusHours(10);
		// TEST-LEAD 원래: assertThat(errors(NOW.plusMinutes(29), farStart))
		assertThat(errors(NOW.plusMinutes(9), farStart)) // TEST-LEAD
			// TEST-LEAD 원래: .containsEntry("hostingStartAt", "호스팅 시작은 지금부터 30분 이후여야 합니다.");
			.containsEntry("hostingStartAt", "호스팅 시작은 지금부터 10분 이후여야 합니다."); // TEST-LEAD
		// TEST-LEAD 원래: assertThat(errors(NOW.plusMinutes(30), farStart)).isEmpty();
		assertThat(errors(NOW.plusMinutes(10), farStart)).isEmpty(); // TEST-LEAD
	}

	@Test
	void pastEventStartAndUncoveredEndAreReported() {
		Map<String, String> errors = new LinkedHashMap<>();
		ApplyService.checkPeriods(NOW.minusHours(1), NOW, NOW.minusHours(3), NOW.minusMinutes(30), NOW, errors);
		assertThat(errors).containsEntry("startAt", "이벤트 시작은 지금보다 뒤여야 합니다.")
			// TEST-LEAD 원래: .containsEntry("hostingStartAt", "호스팅 시작은 지금부터 30분 이후여야 합니다.")
			.containsEntry("hostingStartAt", "호스팅 시작은 지금부터 10분 이후여야 합니다.") // TEST-LEAD
			.containsEntry("hostingEndAt", "호스팅 기간이 이벤트 기간을 포함해야 합니다.");
	}

}
