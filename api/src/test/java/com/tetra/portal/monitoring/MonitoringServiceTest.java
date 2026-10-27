package com.tetra.portal.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tetra.portal.common.time.Kst;
import com.tetra.portal.config.TetraProperties;
import com.tetra.portal.domain.EventStatus;
import com.tetra.portal.domain.HostingStatus;
import com.tetra.portal.domain.platform.Tenant;
import com.tetra.portal.monitoring.MonitoringReader.EventDetail;
import com.tetra.portal.monitoring.MonitoringReader.EventView;
import com.tetra.portal.monitoring.MonitoringResponses.EventMonitoring;
import com.tetra.portal.monitoring.MonitoringResponses.Group;
import com.tetra.portal.monitoring.MonitoringResponses.Service;
import com.tetra.portal.monitoring.MonitoringResponses.StepStatus;
import com.tetra.portal.tenancy.TenantRouter;

/**
 * 캐시에는 Prometheus 결과만 — 상태 · 남은 시간 · 진행 단계는 요청마다 현재 시각으로. 응답 조립(null/0 규칙) · 드롭다운 규칙.
 * DB · Prometheus는 가짜(라우터 · 리더 · 수집기 목), 시각은 바꿀 수 있는 Clock.
 */
class MonitoringServiceTest {

	static final String TENANT = "aaaaaaaaaaaa";
	static final LocalDateTime START = LocalDateTime.of(2026, 10, 27, 15, 0);
	static final LocalDateTime HOSTING_END = START.plusMinutes(40);

	final TenantRouter router = mock(TenantRouter.class);
	final MonitoringReader reader = mock(MonitoringReader.class);
	final MonitoringCollector collector = mock(MonitoringCollector.class);
	final MutableClock clock = new MutableClock(START.minusSeconds(5));
	final MetricsFixture metrics = new MetricsFixture();

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		Tenant tenant = mock(Tenant.class);
		when(tenant.getTenantId()).thenReturn(TENANT);
		when(router.findTenant(TENANT)).thenReturn(tenant);
		when(router.inTenant(any(Tenant.class), any(Supplier.class)))
			.thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
		when(reader.event(eq(1L), any())).thenReturn(detail(EventStatus.OPEN, HostingStatus.HOSTING));
		when(collector.collect(anyString(), anyLong(), any(), anyLong())).thenAnswer(inv -> metrics.build());
	}

	static EventView view(long id, LocalDateTime start, LocalDateTime hostingEnd, EventStatus eventStatus,
			HostingStatus hostingStatus) {
		return new EventView(id, "이벤트 " + id, start.minusMinutes(100), start, start.plusMinutes(40), hostingEnd,
				eventStatus, hostingStatus, 15000L, "M");
	}

	static EventDetail detail(EventStatus eventStatus, HostingStatus hostingStatus) {
		return new EventDetail(view(1, START, HOSTING_END, eventStatus, hostingStatus), 30000L, false,
				"aut26.aaaaaaaaaaaa.example.com", null, null);
	}

	MonitoringService service(String prometheusUrl) {
		var monitoring = new TetraProperties.Monitoring(prometheusUrl, Duration.ofSeconds(2), Duration.ofSeconds(3),
				Duration.ofSeconds(15), Duration.ofSeconds(8));
		var properties = new TetraProperties("test", null, null, null, null, null, null, monitoring, null, "http://localhost:5174");
		return new MonitoringService(router, reader, collector, properties, clock);
	}

	// ---- 캐시와 시각 ----

	@Test
	void 캐시_안에서_시각이_경계를_넘으면_state가_바로_바뀐다() {
		MonitoringService service = service("http://prometheus");

		EventMonitoring before = service.event(TENANT, 1L);
		assertThat(before.state()).isEqualTo(MonitoringPhase.ENTRY_OPEN);
		assertThat(before.header().endsInSec()).isEqualTo(Duration.ofMinutes(40).plusSeconds(5).toSeconds());
		assertThat(before.kpi().waiting()).isZero(); // 시작 전 대기 0

		clock.advance(Duration.ofSeconds(10)); // 15:00:05 — 시작을 넘었다, 캐시 TTL(15초) 안
		EventMonitoring started = service.event(TENANT, 1L);
		assertThat(started.state()).isEqualTo(MonitoringPhase.LIVE);
		assertThat(started.serverTime().toLocalDateTime()).isEqualTo(START.plusSeconds(5));
		assertThat(started.header().endsInSec()).isEqualTo(Duration.ofMinutes(40).minusSeconds(5).toSeconds());
		assertThat(started.steps()).extracting(s -> s.status())
			.containsExactly(StepStatus.DONE, StepStatus.DONE, StepStatus.CURRENT, StepStatus.UPCOMING, StepStatus.UPCOMING);
		assertThat(started.kpi().waiting()).isEqualTo(2400); // 같은 캐시 값이라도 시작 뒤에는 대기 인원
		assertThat(started.lastUpdatedAt()).isEqualTo(before.lastUpdatedAt());

		clock.set(HOSTING_END); // 종료 — 캐시가 남아 있어도 종료 화면
		EventMonitoring ended = service.event(TENANT, 1L);
		assertThat(ended.state()).isEqualTo(MonitoringPhase.ENDED);
		assertThat(ended.kpi()).isNull();
		assertThat(ended.series()).isNull();

		// TEST-LEAD 원래: verify(collector, times(1)).collect(eq(TENANT), eq(1L), eq(START.minusMinutes(60)), anyLong());
		verify(collector, times(1)).collect(eq(TENANT), eq(1L), eq(START.minusMinutes(5)), anyLong()); // TEST-LEAD
	}

	@Test
	void 입장_전과_종료에는_Prometheus를_읽지_않는다() {
		MonitoringService service = service("http://prometheus");
		clock.set(START.minusMinutes(61));
		EventMonitoring before = service.event(TENANT, 1L);
		assertThat(before.state()).isEqualTo(MonitoringPhase.BEFORE_ENTRY);
		assertThat(before.kpi()).isNull();
		assertThat(before.series()).isNull();
		assertThat(before.rejects5m()).isNull();
		assertThat(before.header().service()).isNull();
		assertThat(before.stale()).isFalse();

		clock.set(START.plusMinutes(10));
		when(reader.event(eq(1L), any())).thenReturn(detail(EventStatus.EXPIRED, HostingStatus.ENDED));
		EventMonitoring ended = service.event(TENANT, 1L);
		assertThat(ended.state()).isEqualTo(MonitoringPhase.ENDED);
		assertThat(ended.header().endsInSec()).isNull();
		assertThat(ended.header().service()).isNull();

		verify(collector, never()).collect(anyString(), anyLong(), any(), anyLong());
	}

	@Test
	void PROMETHEUS_URL이_없으면_조회하지_않고_데이터_지연() {
		MonitoringService service = service("");
		clock.set(START.plusMinutes(10));
		EventMonitoring r = service.event(TENANT, 1L);
		assertThat(r.state()).isEqualTo(MonitoringPhase.LIVE);
		assertThat(r.stale()).isTrue();
		assertThat(r.lastUpdatedAt()).isNull();
		assertThat(r.header().service()).isEqualTo(Service.CHECKING);
		// DB 값은 그대로, Prometheus 값은 null
		assertThat(r.kpi().stock()).isEqualTo(15000);
		assertThat(r.kpi().planCapacity()).isEqualTo(30000);
		assertThat(r.kpi().issued()).isNull();
		assertThat(r.series().perMinute()).isEmpty();
		verify(collector, never()).collect(anyString(), anyLong(), any(), anyLong());
	}

	// ---- 응답 조립 ----

	static EventMonitoring assemble(LocalDateTime now, Metrics m, boolean stale) {
		return MonitoringService.assemble(detail(EventStatus.OPEN, HostingStatus.HOSTING), now,
				new MonitoringService.Fetched(m, stale));
	}

	@Test
	void 진행_중_핵심_지표와_추이() {
		LocalDateTime now = START.plusMinutes(12);
		metrics.at = now.atZone(Kst.ZONE).toInstant();
		Instant t0 = START.atZone(Kst.ZONE).toInstant();
		metrics.minute(t0, 600.0, 10.0, 0.0, 15000.0, 6000.0).minute(t0.plusSeconds(60), 30.4, 630.0, 0.0, 14370.0, 7410.0);
		metrics.soldOutTotal = 0.4;
		metrics.rejects5m = 2.6;

		EventMonitoring r = assemble(now, metrics.build(), false);

		assertThat(r.state()).isEqualTo(MonitoringPhase.LIVE);
		assertThat(r.stale()).isFalse();
		assertThat(r.header().service()).isEqualTo(Service.NORMAL);
		assertThat(r.header().soldOutAt()).isNull();
		assertThat(r.kpi().issued()).isEqualTo(15000 - 5460); // 총 재고 − 남은 재고
		assertThat(r.kpi().soldOutCount()).isZero();
		assertThat(r.kpi().waiting()).isEqualTo(2400);
		assertThat(r.kpi().waitMinutes()).isEqualTo(4);
		assertThat(r.kpi().activeSessions()).isEqualTo(6820);
		assertThat(r.kpi().planName()).isEqualTo("M");
		assertThat(r.kpi().peak().value()).isEqualTo(7410);
		assertThat(r.kpi().peak().at().toLocalDateTime()).isEqualTo(START.plusMinutes(1));
		assertThat(r.series().perMinute()).hasSize(2);
		assertThat(r.series().perMinute().get(1).entries()).isEqualTo(30);
		assertThat(r.series().perMinute().get(1).success()).isEqualTo(630);
		assertThat(r.series().stock().get(1).remaining()).isEqualTo(14370);
		// 15:12 + 5460 ÷ 10/초 = 546초
		assertThat(r.series().projection().at().toLocalDateTime()).isEqualTo(now.plusSeconds(546));
		assertThat(r.rejects5m()).isEqualTo(3);
		assertThat(r.lastUpdatedAt().toLocalDateTime()).isEqualTo(now);
	}

	@Test
	void 재고가_0이면_품절_시각과_품절_상태() {
		LocalDateTime now = START.plusMinutes(20);
		metrics.at = now.atZone(Kst.ZONE).toInstant();
		Instant t0 = START.plusMinutes(17).atZone(Kst.ZONE).toInstant();
		metrics.minute(t0, 10.0, 100.0, 0.0, 30.0, 7000.0).minute(t0.plusSeconds(60), 5.0, 30.0, 20.0, 0.0, 7000.0);
		metrics.stockRemaining = 0.0;
		metrics.soldOutTotal = 1234.0;

		EventMonitoring r = assemble(now, metrics.build(), false);

		assertThat(r.state()).isEqualTo(MonitoringPhase.SOLD_OUT);
		assertThat(r.header().soldOutAt().toLocalDateTime()).isEqualTo(START.plusMinutes(18));
		assertThat(r.kpi().issued()).isEqualTo(15000);
		assertThat(r.kpi().soldOutCount()).isEqualTo(1234);
		assertThat(r.kpi().waitMinutes()).isNull(); // 품절 안내
		assertThat(r.series().projection()).isNull();
		assertThat(r.steps().get(2).status()).isEqualTo(StepStatus.CURRENT);
	}

	@Test
	void 새_지표가_없으면_그_칸만_null() {
		LocalDateTime now = START.plusMinutes(12);
		metrics.at = now.atZone(Kst.ZONE).toInstant();
		metrics.sessionsActive = null;
		metrics.rejects5m = null;
		metrics.minute(START.atZone(Kst.ZONE).toInstant(), 600.0, 10.0, 0.0, 15000.0, null);

		EventMonitoring r = assemble(now, metrics.build(), false);

		assertThat(r.kpi().activeSessions()).isNull();
		assertThat(r.kpi().peak()).isNull();
		assertThat(r.rejects5m()).isNull();
		assertThat(r.kpi().issued()).isEqualTo(15000 - 5460);
		assertThat(r.kpi().waiting()).isEqualTo(2400);
		assertThat(r.header().service()).isEqualTo(Service.NORMAL);
	}

	@Test
	void 재고를_못_읽으면_발급_성공도_null이고_품절로_보지_않는다() {
		LocalDateTime now = START.plusMinutes(12);
		metrics.at = now.atZone(Kst.ZONE).toInstant();
		metrics.stockRemaining = null; // Redis를 못 읽은 게이지(NaN)

		EventMonitoring r = assemble(now, metrics.build(), false);

		assertThat(r.state()).isEqualTo(MonitoringPhase.LIVE);
		assertThat(r.kpi().issued()).isNull();
		assertThat(r.kpi().stock()).isEqualTo(15000);
	}

	@Test
	void 데이터_지연이면_마지막_값과_확인_중() {
		LocalDateTime now = START.plusMinutes(12);
		metrics.at = START.plusMinutes(11).atZone(Kst.ZONE).toInstant();

		EventMonitoring r = assemble(now, metrics.build(), true);

		assertThat(r.stale()).isTrue();
		assertThat(r.header().service()).isEqualTo(Service.CHECKING);
		assertThat(r.lastUpdatedAt().toLocalDateTime()).isEqualTo(START.plusMinutes(11));
		assertThat(r.kpi().waiting()).isEqualTo(2400);
	}

	@Test
	void 준비_실패는_입장_전처럼_보이고_안내만() {
		when(reader.event(eq(1L), any())).thenReturn(detail(EventStatus.OPEN_FAILED, HostingStatus.HOSTING));
		clock.set(START.minusMinutes(30));
		EventMonitoring r = service("http://prometheus").event(TENANT, 1L);
		assertThat(r.state()).isEqualTo(MonitoringPhase.BEFORE_ENTRY);
		assertThat(r.prepareFailed()).isTrue();
		assertThat(r.kpi()).isNull();
		verify(collector, never()).collect(anyString(), anyLong(), any(), anyLong());
	}

	@Test
	void 배포_호스팅_시작_실패는_빈_상태만_Prometheus를_읽지_않는다() {
		MonitoringService service = service("http://prometheus");
		clock.set(START.plusMinutes(10)); // 시각으로는 진행 중
		for (EventDetail d : List.of(detail(EventStatus.SCHEDULED, HostingStatus.DEPLOY_FAILED),
				detail(EventStatus.OPEN_FAILED, HostingStatus.HOSTING_FAILED))) {
			when(reader.event(eq(1L), any())).thenReturn(d);
			EventMonitoring r = service.event(TENANT, 1L);
			assertThat(r.state()).isEqualTo(MonitoringPhase.UNAVAILABLE);
			assertThat(r.prepareFailed()).isFalse(); // 준비 실패 안내 대신 빈 상태
			assertThat(r.steps()).isEmpty();
			assertThat(r.header().endsInSec()).isNull();
			assertThat(r.header().soldOutAt()).isNull();
			assertThat(r.header().service()).isNull();
			assertThat(r.kpi()).isNull();
			assertThat(r.series()).isNull();
			assertThat(r.rejects5m()).isNull();
			assertThat(r.period().startAt().toLocalDateTime()).isEqualTo(START); // 이벤트 기간은 그대로
		}
		verify(collector, never()).collect(anyString(), anyLong(), any(), anyLong());

		// 재실행으로 HOSTING이 되면 다시 진행 중
		when(reader.event(eq(1L), any())).thenReturn(detail(EventStatus.OPEN, HostingStatus.HOSTING));
		assertThat(service.event(TENANT, 1L).state()).isEqualTo(MonitoringPhase.LIVE);
	}

	// ---- 드롭다운 ----

	static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 14, 30);

	@Test
	void 진행_중_오늘_예정_최근_종료_순서이고_내일_예정과_8일_전_종료는_뺀다() {
		var live = view(1, NOW.minusMinutes(10), NOW.plusMinutes(30), EventStatus.OPEN, HostingStatus.HOSTING);
		// TEST-LEAD 원래: var entry = view(2, NOW.plusMinutes(20), NOW.plusMinutes(90), EventStatus.OPEN, HostingStatus.HOSTING);
		var entry = view(2, NOW.plusMinutes(3), NOW.plusMinutes(90), EventStatus.OPEN, HostingStatus.HOSTING); // TEST-LEAD
		var todayLater = view(3, NOW.plusHours(6), NOW.plusHours(7), EventStatus.SCHEDULED, HostingStatus.DEPLOYED);
		var todaySooner = view(4, NOW.plusHours(2), NOW.plusHours(3), EventStatus.SCHEDULED, HostingStatus.DEPLOYED);
		var tomorrow = view(5, NOW.plusDays(1), NOW.plusDays(1).plusHours(1), EventStatus.SCHEDULED, HostingStatus.PENDING);
		var endedRecent = view(6, NOW.minusDays(1), NOW.minusDays(1).plusHours(1), EventStatus.EXPIRED, HostingStatus.ENDED);
		var endedWeek = view(7, NOW.minusDays(7).minusHours(1), NOW.minusDays(7), EventStatus.EXPIRED, HostingStatus.ENDED);
		var endedOld = view(8, NOW.minusDays(8).minusHours(1), NOW.minusDays(7).minusSeconds(1), EventStatus.EXPIRED,
				HostingStatus.ENDED);

		var list = MonitoringService.eventList(
				List.of(endedOld, todayLater, tomorrow, endedWeek, live, entry, endedRecent, todaySooner), NOW);

		assertThat(list.events()).extracting(i -> i.eventId()).containsExactly(1L, 2L, 4L, 3L, 6L, 7L);
		assertThat(list.events()).extracting(i -> i.group())
			.containsExactly(Group.LIVE, Group.LIVE, Group.TODAY, Group.TODAY, Group.ENDED, Group.ENDED);
		assertThat(list.events()).extracting(i -> i.state())
			.containsExactly(MonitoringPhase.LIVE, MonitoringPhase.ENTRY_OPEN, MonitoringPhase.BEFORE_ENTRY,
					MonitoringPhase.BEFORE_ENTRY, MonitoringPhase.ENDED, MonitoringPhase.ENDED);
		assertThat(list.defaultEventId()).isEqualTo(1L);
	}

	@Test
	void 기본_선택은_진행_중_입장_중_가까운_예정_최근_종료_순() {
		// TEST-LEAD 원래: var entry = view(2, NOW.plusMinutes(20), NOW.plusMinutes(90), EventStatus.OPEN, HostingStatus.HOSTING);
		var entry = view(2, NOW.plusMinutes(3), NOW.plusMinutes(90), EventStatus.OPEN, HostingStatus.HOSTING); // TEST-LEAD
		var today = view(4, NOW.plusHours(2), NOW.plusHours(3), EventStatus.SCHEDULED, HostingStatus.DEPLOYED);
		var ended = view(6, NOW.minusDays(1), NOW.minusDays(1).plusHours(1), EventStatus.EXPIRED, HostingStatus.ENDED);
		var ended2 = view(9, NOW.minusDays(2), NOW.minusDays(2).plusHours(1), EventStatus.EXPIRED, HostingStatus.ENDED);

		assertThat(MonitoringService.eventList(List.of(today, entry, ended), NOW).defaultEventId()).isEqualTo(2L);
		assertThat(MonitoringService.eventList(List.of(ended, today), NOW).defaultEventId()).isEqualTo(4L);
		assertThat(MonitoringService.eventList(List.of(ended2, ended), NOW).defaultEventId()).isEqualTo(6L);
		var empty = MonitoringService.eventList(List.of(), NOW);
		assertThat(empty.events()).isEmpty();
		assertThat(empty.defaultEventId()).isNull();
	}

	@Test
	void 배포_호스팅_시작_실패는_목록과_기본_선택에서_빼고_회복되면_다시_나온다() {
		var deployFailed = view(10, NOW.minusMinutes(10), NOW.plusMinutes(30), EventStatus.SCHEDULED,
				HostingStatus.DEPLOY_FAILED);
		var hostingFailed = view(11, NOW.minusMinutes(10), NOW.plusMinutes(30), EventStatus.OPEN_FAILED,
				HostingStatus.HOSTING_FAILED);
		// 종료 시각이 지난 배포 실패 — 지금은 "종료"로 보이던 경우
		var deployFailedEnded = view(12, NOW.minusDays(1), NOW.minusDays(1).plusHours(1), EventStatus.SCHEDULED,
				HostingStatus.DEPLOY_FAILED);
		var today = view(4, NOW.plusHours(2), NOW.plusHours(3), EventStatus.SCHEDULED, HostingStatus.DEPLOYED);

		var list = MonitoringService.eventList(List.of(deployFailed, hostingFailed, deployFailedEnded, today), NOW);
		assertThat(list.events()).extracting(i -> i.eventId()).containsExactly(4L);
		assertThat(list.defaultEventId()).isEqualTo(4L);
		assertThat(MonitoringService.eventList(List.of(deployFailed, hostingFailed), NOW).defaultEventId()).isNull();

		var recovered = view(11, NOW.minusMinutes(10), NOW.plusMinutes(30), EventStatus.OPEN, HostingStatus.HOSTING);
		var again = MonitoringService.eventList(List.of(deployFailed, recovered, today), NOW);
		assertThat(again.events()).extracting(i -> i.eventId()).containsExactly(11L, 4L);
		assertThat(again.defaultEventId()).isEqualTo(11L);
	}

	@Test
	void 준비_실패도_오늘_예정에_보인다() {
		var failed = view(3, NOW.plusMinutes(10), NOW.plusHours(2), EventStatus.OPEN_FAILED, HostingStatus.HOSTING);
		var list = MonitoringService.eventList(List.of(failed), NOW);
		assertThat(list.events()).singleElement().satisfies(i -> {
			assertThat(i.group()).isEqualTo(Group.TODAY);
			assertThat(i.prepareFailed()).isTrue();
		});
	}

	static final class MutableClock extends Clock {

		private Instant now;

		MutableClock(LocalDateTime kst) {
			set(kst);
		}

		void set(LocalDateTime kst) {
			now = kst.atZone(Kst.ZONE).toInstant();
		}

		void advance(Duration d) {
			now = now.plus(d);
		}

		@Override
		public ZoneId getZone() {
			return Kst.ZONE;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}

	}

}
