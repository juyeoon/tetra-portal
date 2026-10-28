package com.tetra.portal.monitoring;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.tetra.portal.common.error.ApiException;
import com.tetra.portal.common.error.ErrorCode;
import com.tetra.portal.domain.ApplicationStatus;
import com.tetra.portal.domain.EventStatus;
import com.tetra.portal.domain.HostingStatus;
import com.tetra.portal.domain.StatusType;
import com.tetra.portal.domain.platform.Tenant;
import com.tetra.portal.domain.platform.TrafficPlan;
import com.tetra.portal.domain.platform.TrafficPlanRepository;
import com.tetra.portal.domain.tenant.Event;
import com.tetra.portal.domain.tenant.EventRepository;
import com.tetra.portal.domain.tenant.EventResultSummary;
import com.tetra.portal.domain.tenant.EventResultSummaryRepository;
import com.tetra.portal.domain.tenant.EventStatusLog;
import com.tetra.portal.domain.tenant.EventStatusLogRepository;
import com.tetra.portal.event.EventDomain;
import com.tetra.portal.result.ResultReader;
import com.tetra.portal.result.ResultResponse;
import com.tetra.portal.tenancy.TenantContext;

import jakarta.persistence.EntityManager;

/**
 * 모니터링 DB 읽기 — TenantRouter.inTenant 안에서만. 승인된(호스팅 대상) 이벤트만 모니터링 대상이다.
 * 다른 테넌트의 이벤트는 이 스키마에 없으므로 EVENT_NOT_FOUND(결과 탭과 같은 소유 확인).
 */
@Component
class MonitoringReader {

	/** 시각 · 상태 — 목록과 상세가 같이 쓴다 */
	record EventView(Long eventId, String name, LocalDateTime hostingStartAt, LocalDateTime startAt, LocalDateTime endAt,
			LocalDateTime hostingEndAt, EventStatus eventStatus, HostingStatus hostingStatus, Long stockCount,
			String planCode) {
	}

	/**
	 * 상세에만 필요한 값. planCapacity = traffic_plan.max_capacity(결과 탭과 같은 출처). resultState = 결과 탭 판정(READY · END_FAILED …).
	 * hostingStartedAt · openedAt = 실제 전이 시각(event_status_log의 첫 HOSTING → HOSTING · EVENT → OPEN, 없으면 null).
	 * soldOutAt = 종료 뒤 품절 시각(분) — 결과가 준비되고 재고를 다 발급했으면 마지막 발급 성공 분(발급 이력), 그 밖 null
	 */
	record EventDetail(EventView event, Long planCapacity, ResultResponse.State resultState, String domain,
			LocalDateTime hostingStartedAt, LocalDateTime openedAt, LocalDateTime soldOutAt) {
	}

	private final EventRepository events;

	private final EventResultSummaryRepository summaries;

	private final TrafficPlanRepository plans;

	private final EventStatusLogRepository statusLogs;

	private final EventDomain domains;

	private final EntityManager entityManager;

	MonitoringReader(EventRepository events, EventResultSummaryRepository summaries, TrafficPlanRepository plans,
			EventStatusLogRepository statusLogs, EventDomain domains, EntityManager entityManager) {
		this.events = events;
		this.summaries = summaries;
		this.plans = plans;
		this.statusLogs = statusLogs;
		this.domains = domains;
		this.entityManager = entityManager;
	}

	@Transactional(readOnly = true)
	public List<EventView> approvedEvents() {
		return events.findByApplicationStatus(ApplicationStatus.APPROVED).stream().map(MonitoringReader::view).toList();
	}

	@Transactional(readOnly = true)
	public EventDetail event(Long eventId, Tenant tenant) {
		Event e = events.findById(eventId)
			.filter(ev -> ev.getApplicationStatus() == ApplicationStatus.APPROVED)
			.orElseThrow(() -> new ApiException(ErrorCode.EVENT_NOT_FOUND));
		Long capacity = plans.findById(e.getTrafficPlanCode()).map(TrafficPlan::getMaxCapacity).orElse(null);
		EventResultSummary summary = summaries.findById(eventId).orElse(null);
		ResultResponse.State resultState = ResultReader.state(e, summary);
		List<EventStatusLog> logs = statusLogs.findByEventIdOrderByChangedAtAscLogIdAsc(eventId);
		return new EventDetail(view(e), capacity, resultState, domains.of(e),
				firstAt(logs, StatusType.HOSTING, HostingStatus.HOSTING), firstAt(logs, StatusType.EVENT, EventStatus.OPEN),
				resultState == ResultResponse.State.READY ? soldOutAt(e, summary) : null);
	}

	/**
	 * 재고를 다 발급했으면(성공 수 ≥ 총 재고 — 결과 탭과 같은 event.stock_count) 마지막 발급 성공 분. 종료 뒤에는 Prometheus를 읽지 않아
	 * 발급 이력으로 정한다 — 결과가 준비된 뒤에만(발급 이력 저장 API) 있다. 다른 테넌트 행은 tenant_id로 거른다(엔티티를 거치지 않는 읽기).
	 */
	private LocalDateTime soldOutAt(Event e, EventResultSummary summary) {
		Long stock = e.getStockCount();
		if (summary.getSuccessCount() == null || stock == null || stock <= 0 || summary.getSuccessCount() < stock) {
			return null;
		}
		Object minute = entityManager.createNativeQuery("""
				select date_format(max(issued_at), '%Y-%m-%d %H:%i:00')
				  from issuance_history
				 where event_id = :eventId and tenant_id = :tenantId and result = 'SUCCESS'""")
			.setParameter("eventId", e.getEventId())
			.setParameter("tenantId", TenantContext.current())
			.getSingleResult();
		return minute == null ? null : LocalDateTime.parse(minute.toString().replace(' ', 'T'));
	}

	/** 로그는 시각 순서 — 처음 그 상태가 된 시각 */
	private static LocalDateTime firstAt(List<EventStatusLog> logs, StatusType type, Enum<?> status) {
		return logs.stream()
			.filter(l -> l.getStatusType() == type && status.name().equals(l.getNewStatus()))
			.findFirst()
			.map(EventStatusLog::getChangedAt)
			.orElse(null);
	}

	private static EventView view(Event e) {
		return new EventView(e.getEventId(), e.getName(), e.getHostingStartAt(), e.getStartAt(), e.getEndAt(),
				e.getHostingEndAt(), e.getEventStatus(), e.getHostingStatus(), e.getStockCount(), e.getTrafficPlanCode());
	}

}
