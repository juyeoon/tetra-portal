package com.tetra.portal.monitoring;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.stereotype.Service;

import com.tetra.portal.common.time.Kst;
import com.tetra.portal.config.TetraProperties;
import com.tetra.portal.domain.EventStatus;
import com.tetra.portal.domain.platform.Tenant;
import com.tetra.portal.monitoring.MonitoringReader.EventDetail;
import com.tetra.portal.monitoring.MonitoringReader.EventView;
import com.tetra.portal.monitoring.MonitoringResponses.EventItem;
import com.tetra.portal.monitoring.MonitoringResponses.EventList;
import com.tetra.portal.monitoring.MonitoringResponses.EventMonitoring;
import com.tetra.portal.monitoring.MonitoringResponses.Group;
import com.tetra.portal.monitoring.MonitoringResponses.Header;
import com.tetra.portal.monitoring.MonitoringResponses.Kpi;
import com.tetra.portal.monitoring.MonitoringResponses.MinutePoint;
import com.tetra.portal.monitoring.MonitoringResponses.Peak;
import com.tetra.portal.monitoring.MonitoringResponses.Period;
import com.tetra.portal.monitoring.MonitoringResponses.Projection;
import com.tetra.portal.monitoring.MonitoringResponses.Series;
import com.tetra.portal.monitoring.MonitoringResponses.StockPoint;
import com.tetra.portal.tenancy.TenantRouter;

/**
 * UI-TP-11 모니터링. DB(소유 확인 · 시각 · 재고 · 플랜)는 요청마다 읽고, Prometheus 결과({@link Metrics})만 이벤트별로 캐시한다
 * (키 = tenant + eventId + 입장 시각, TTL 15초 + single-flight, ECS 태스크마다 따로).
 * 상태 · 남은 시간 · 진행 단계는 요청마다 현재 시각으로 계산 — 캐시 안에서 입장 · 시작 · 종료 시각을 넘어도 바로 바뀐다.
 * Prometheus는 입장 ~ 종료 상태에서만 읽는다. 실패 규칙은 Console과 같다(서버 문제 → 마지막 값 + stale, 쿼리 하나 → 그 값만 null).
 */
@Service
public class MonitoringService {

	/** 드롭다운의 최근 종료 범위 */
	static final Duration RECENT_ENDED = Duration.ofDays(7);

	record MetricsKey(String tenantId, long eventId, LocalDateTime entryAt) {
	}

	/** 캐시 값. metrics가 null이면 아직 한 번도 성공하지 못함 */
	record Fetched(Metrics metrics, boolean stale) {

		static Fetched unavailable() {
			return new Fetched(null, true);
		}

		Fetched asStale() {
			return new Fetched(metrics, true);
		}

	}

	private final TenantRouter router;

	private final MonitoringReader reader;

	private final Clock clock;

	private final boolean configured;

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	private final KeyedRefreshCache<MetricsKey, Fetched> cache;

	MonitoringService(TenantRouter router, MonitoringReader reader, MonitoringCollector collector,
			TetraProperties properties, Clock clock) {
		this.router = router;
		this.reader = reader;
		this.clock = clock;
		TetraProperties.Monitoring monitoring = properties.monitoring();
		this.configured = monitoring.configured();
		this.cache = new KeyedRefreshCache<>(key -> new RefreshCache<>("모니터링 · Prometheus", monitoring.cacheTtl(),
				monitoring.refreshTimeout(), executor,
				deadline -> new Fetched(collector.collect(key.tenantId(), key.eventId(), key.entryAt(), deadline), false),
				Fetched::asStale, Fetched::unavailable));
	}

	public EventList events(String tenantId) {
		return eventList(router.inTenant(tenantId, reader::approvedEvents), Kst.now(clock));
	}

	public EventMonitoring event(String tenantId, Long eventId) {
		Tenant tenant = router.findTenant(tenantId);
		EventDetail detail = router.inTenant(tenant, () -> reader.event(eventId, tenant));
		EventView e = detail.event();
		LocalDateTime entryAt = MonitoringPhase.entryAt(e.startAt());
		MonitoringPhase phase = phaseOf(e, Kst.now(clock));
		Fetched fetched = null;
		if (phase.queriesPrometheus()) {
			fetched = configured ? cache.get(new MetricsKey(tenant.getTenantId(), e.eventId(), entryAt))
					: Fetched.unavailable();
		}
		// 캐시를 기다리는 동안 시각이 지났을 수 있어 응답 시각은 여기서 다시 잡는다
		return assemble(detail, Kst.now(clock), fetched);
	}

	static MonitoringPhase phaseOf(EventView e, LocalDateTime now) {
		return MonitoringPhase.of(e.startAt(), e.hostingEndAt(), e.eventStatus(), e.hostingStatus(), now);
	}

	/** 응답 조립. fetched = 이번에 쓴 Prometheus 결과(조회하지 않는 상태면 null) */
	static EventMonitoring assemble(EventDetail detail, LocalDateTime now, Fetched fetched) {
		EventView e = detail.event();
		LocalDateTime entryAt = MonitoringPhase.entryAt(e.startAt());
		Metrics m = fetched == null ? null : fetched.metrics();
		Long remaining = m == null ? null : MonitoringRules.count(m.stockRemaining());
		MonitoringPhase phase = phaseOf(e, now).withRemaining(remaining);
		boolean queried = phase.queriesPrometheus();
		boolean stale = queried && fetched != null && fetched.stale();

		Instant soldOutAt = phase == MonitoringPhase.SOLD_OUT
				? MonitoringRules.soldOutAt(m.grid(), m.stock(), remaining, m.at()) : null;
		Header header = new Header(phase == MonitoringPhase.ENDED ? null
				: Math.max(0, Duration.between(now, e.hostingEndAt()).toSeconds()), offset(soldOutAt),
				queried ? MonitoringRules.service(m, stale) : null);
		return new EventMonitoring(e.eventId(), e.name(), phase,
				e.eventStatus() == EventStatus.OPEN_FAILED && phase != MonitoringPhase.ENDED, stale,
				m == null ? null : offset(m.at()), Kst.toOffset(now),
				new Period(Kst.toOffset(e.hostingStartAt()), Kst.toOffset(entryAt), Kst.toOffset(e.startAt()),
						Kst.toOffset(e.endAt()), Kst.toOffset(e.hostingEndAt()), detail.domain()),
				MonitoringRules.steps(phase, now, e.hostingStartAt(), entryAt, e.startAt(), e.hostingEndAt(),
						detail.resultReady()),
				header, queried ? kpi(detail, phase, m, remaining) : null, queried ? series(e, phase, m, remaining) : null,
				queried && m != null ? MonitoringRules.count(m.rejects5m()) : null);
	}

	/** DB 값(재고 · 플랜)은 항상, Prometheus 값은 있을 때만 */
	private static Kpi kpi(EventDetail detail, MonitoringPhase phase, Metrics m, Long remaining) {
		EventView e = detail.event();
		Long stock = e.stockCount();
		if (m == null) {
			return new Kpi(null, stock, null, null, null, null, detail.planCapacity(), e.planCode(), null);
		}
		Long issued = remaining == null ? null : Math.max(0, Math.min(stock, stock - remaining));
		// 시작 전에는 커서가 움직이지 않아 번호표 수가 그대로 대기로 보인다 → 0(명세 3절)
		Long waiting = phase == MonitoringPhase.ENTRY_OPEN ? Long.valueOf(0) : MonitoringRules.count(m.waiting());
		MonitoringRules.PeakAt peak = MonitoringRules.peak(m.grid(), m.sessions(), m.sessionsActive(), m.at());
		return new Kpi(issued, stock, MonitoringRules.count(m.soldOutTotal()), waiting,
				MonitoringRules.waitMinutes(phase, waiting, m.processedLastMinute()),
				MonitoringRules.count(m.sessionsActive()), detail.planCapacity(), e.planCode(),
				peak == null ? null : new Peak(peak.value(), offset(peak.at())));
	}

	private static Series series(EventView e, MonitoringPhase phase, Metrics m, Long remaining) {
		if (m == null) {
			return new Series(List.of(), List.of(), null);
		}
		List<MinutePoint> perMinute = new ArrayList<>(m.grid().size());
		List<StockPoint> stock = new ArrayList<>(m.grid().size());
		for (int i = 0; i < m.grid().size(); i++) {
			var t = offset(Instant.ofEpochMilli(m.grid().get(i)));
			perMinute.add(new MinutePoint(t, MonitoringRules.count(m.entries().get(i)),
					MonitoringRules.count(m.success().get(i)), MonitoringRules.count(m.soldOut().get(i))));
			stock.add(new StockPoint(t, MonitoringRules.count(m.stock().get(i))));
		}
		Instant projection = MonitoringRules.projection(phase, e.startAt().atZone(Kst.ZONE).toInstant(), m.at(),
				remaining, m.successRate5m());
		return new Series(perMinute, stock, projection == null ? null : new Projection(offset(projection)));
	}

	/**
	 * 드롭다운(명세 5절): 승인된 이벤트 중 진행 중(입장 ~ 종료) → 오늘 시작 예정 → 최근 7일 종료. 내일 이후 예정은 넣지 않는다.
	 * 기본 선택: 시작한 이벤트 → 입장 중 → 가장 가까운 예정 → 가장 최근 종료.
	 */
	static EventList eventList(List<EventView> views, LocalDateTime now) {
		List<EventItem> live = new ArrayList<>();
		List<EventItem> today = new ArrayList<>();
		List<EventItem> ended = new ArrayList<>();
		for (EventView e : views.stream().sorted(Comparator.comparing(EventView::startAt).thenComparing(EventView::eventId)).toList()) {
			MonitoringPhase phase = phaseOf(e, now);
			switch (phase) {
				case ENTRY_OPEN, LIVE, SOLD_OUT -> live.add(item(e, Group.LIVE, phase));
				case BEFORE_ENTRY -> {
					if (!e.startAt().toLocalDate().isAfter(now.toLocalDate())) {
						today.add(item(e, Group.TODAY, phase));
					}
				}
				case ENDED -> {
					if (!e.hostingEndAt().isBefore(now.minus(RECENT_ENDED))) {
						ended.add(item(e, Group.ENDED, phase));
					}
				}
			}
		}
		ended.sort(Comparator.comparing(EventItem::hostingEndAt).reversed().thenComparing(EventItem::eventId));
		List<EventItem> all = new ArrayList<>(live);
		all.addAll(today);
		all.addAll(ended);
		Long defaultId = live.stream()
			.filter(i -> i.state() == MonitoringPhase.LIVE)
			.findFirst()
			.or(() -> live.stream().findFirst())
			.or(() -> today.stream().findFirst())
			.or(() -> ended.stream().findFirst())
			.map(EventItem::eventId)
			.orElse(null);
		return new EventList(all, defaultId);
	}

	private static EventItem item(EventView e, Group group, MonitoringPhase phase) {
		return new EventItem(e.eventId(), e.name(), group, phase, e.eventStatus() == EventStatus.OPEN_FAILED,
				Kst.toOffset(MonitoringPhase.entryAt(e.startAt())), Kst.toOffset(e.startAt()),
				Kst.toOffset(e.hostingEndAt()));
	}

	private static OffsetDateTime offset(Instant at) {
		return at == null ? null : at.atZone(Kst.ZONE).toOffsetDateTime();
	}

}
