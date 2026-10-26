package com.tetra.portal.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.tetra.portal.domain.EventStatus;
import com.tetra.portal.domain.HostingStatus;

/** 상태 판정 경계(명세 4절). 입장 = 시작 − 60분, 종료 = hosting_end_at. */
class MonitoringPhaseTest {

	static final LocalDateTime START = LocalDateTime.of(2026, 10, 27, 15, 0);
	static final LocalDateTime ENTRY = START.minusMinutes(60);
	static final LocalDateTime HOSTING_END = START.plusMinutes(100);

	static MonitoringPhase at(LocalDateTime now) {
		return MonitoringPhase.of(START, HOSTING_END, EventStatus.SCHEDULED, HostingStatus.HOSTING, now);
	}

	@Test
	void 시각_경계() {
		assertThat(at(ENTRY.minusSeconds(1))).isEqualTo(MonitoringPhase.BEFORE_ENTRY);
		assertThat(at(ENTRY)).isEqualTo(MonitoringPhase.ENTRY_OPEN);
		assertThat(at(START.minusSeconds(1))).isEqualTo(MonitoringPhase.ENTRY_OPEN);
		assertThat(at(START)).isEqualTo(MonitoringPhase.LIVE);
		assertThat(at(HOSTING_END.minusSeconds(1))).isEqualTo(MonitoringPhase.LIVE);
		assertThat(at(HOSTING_END)).isEqualTo(MonitoringPhase.ENDED);
	}

	@Test
	void 종료_결과가_오면_시각과_관계없이_종료() {
		LocalDateTime live = START.plusMinutes(10);
		assertThat(MonitoringPhase.of(START, HOSTING_END, EventStatus.EXPIRED, HostingStatus.HOSTING, live))
			.isEqualTo(MonitoringPhase.ENDED);
		assertThat(MonitoringPhase.of(START, HOSTING_END, EventStatus.OPEN, HostingStatus.ENDED, live))
			.isEqualTo(MonitoringPhase.ENDED);
		assertThat(MonitoringPhase.of(START, HOSTING_END, EventStatus.OPEN, HostingStatus.END_FAILED, live))
			.isEqualTo(MonitoringPhase.ENDED);
	}

	@Test
	void 준비_실패는_종료_전까지_입장_전처럼_보인다() {
		assertThat(MonitoringPhase.of(START, HOSTING_END, EventStatus.OPEN_FAILED, HostingStatus.HOSTING, START.plusMinutes(5)))
			.isEqualTo(MonitoringPhase.BEFORE_ENTRY);
		assertThat(MonitoringPhase.of(START, HOSTING_END, EventStatus.OPEN_FAILED, HostingStatus.HOSTING, HOSTING_END))
			.isEqualTo(MonitoringPhase.ENDED);
	}

	@Test
	void 품절은_진행_중에_재고가_0일_때만() {
		assertThat(MonitoringPhase.LIVE.withRemaining(0L)).isEqualTo(MonitoringPhase.SOLD_OUT);
		assertThat(MonitoringPhase.LIVE.withRemaining(1L)).isEqualTo(MonitoringPhase.LIVE);
		assertThat(MonitoringPhase.LIVE.withRemaining(null)).isEqualTo(MonitoringPhase.LIVE);
		assertThat(MonitoringPhase.ENTRY_OPEN.withRemaining(0L)).isEqualTo(MonitoringPhase.ENTRY_OPEN);
		assertThat(MonitoringPhase.ENDED.withRemaining(0L)).isEqualTo(MonitoringPhase.ENDED);
	}

	@Test
	void Prometheus는_입장부터_종료_전까지만_조회한다() {
		assertThat(MonitoringPhase.values()).filteredOn(MonitoringPhase::queriesPrometheus)
			.containsExactly(MonitoringPhase.ENTRY_OPEN, MonitoringPhase.LIVE, MonitoringPhase.SOLD_OUT);
	}

}
