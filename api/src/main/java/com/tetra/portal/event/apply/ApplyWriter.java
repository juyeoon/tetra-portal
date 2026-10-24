package com.tetra.portal.event.apply;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.tetra.portal.common.time.Kst;
import com.tetra.portal.domain.AccountStatus;
import com.tetra.portal.domain.ApplicationStatus;
import com.tetra.portal.domain.EventType;
import com.tetra.portal.domain.platform.EventRegistry;
import com.tetra.portal.domain.platform.EventRegistryRepository;
import com.tetra.portal.domain.platform.TenantRepository;
import com.tetra.portal.domain.platform.TenantStatusLog;
import com.tetra.portal.domain.tenant.Coupon;
import com.tetra.portal.domain.tenant.Event;
import com.tetra.portal.domain.tenant.EventBilling;
import com.tetra.portal.domain.tenant.EventRepository;
import com.tetra.portal.domain.tenant.EventStatusLog;
import com.tetra.portal.notification.NotificationService;

import jakarta.persistence.EntityManager;

/**
 * WF1-1 트랜잭션 a · b · c. 모두 TenantRouter.inTenant 안에서 부른다 — 커넥션의 기본 스키마가 t_{tenant_id}이고,
 * tetra_platform 테이블은 엔티티의 catalog로 같은 커넥션 · 같은 트랜잭션에서 바뀐다.
 * 순서: registry 먼저, 테넌트 스키마 event 뒤(Console과 같음).
 */
@Component
class ApplyWriter {

	private final EntityManager em;

	private final EventRegistryRepository registries;

	private final EventRepository events;

	private final TenantRepository tenants;

	private final NotificationService notifications;

	private final Clock clock;

	ApplyWriter(EntityManager em, EventRegistryRepository registries, EventRepository events, TenantRepository tenants,
			NotificationService notifications, Clock clock) {
		this.em = em;
		this.registries = registries;
		this.events = events;
		this.tenants = tenants;
		this.notifications = notifications;
		this.clock = clock;
	}

	/** 검사를 마친 신청. 일시는 KST 초 단위. */
	record Application(String tenantId, String name, EventType eventType, Long templateId, String layoutJson,
			LocalDateTime startAt, LocalDateTime endAt, LocalDateTime hostingStartAt, LocalDateTime hostingEndAt,
			String endpointUrl, String trafficPlanCode, Long stockCount, List<ApplyRequest.CouponInput> coupons,
			BigDecimal hourlyPrice, BigDecimal hostingHourlyPrice, BigDecimal estimatedCost) {
	}

	/** (a) 접수 — registry · event APPLYING, 배너 경로 없음. event_id를 돌려준다. */
	@Transactional
	public Long create(Application a) {
		LocalDateTime now = Kst.now(clock);
		EventRegistry registry = EventRegistry.applying(a.tenantId(), a.name(), a.eventType(), a.trafficPlanCode(),
				a.hostingStartAt(), a.startAt(), now);
		em.persist(registry);
		em.flush(); // event_id(AUTO_INCREMENT) 확정
		Long eventId = registry.getEventId();

		em.persist(Event.applying(eventId, a.tenantId(), a.name(), a.eventType(), a.templateId(), a.layoutJson(),
				a.startAt(), a.endAt(), a.hostingStartAt(), a.hostingEndAt(), a.endpointUrl(), a.trafficPlanCode(),
				a.stockCount(), now));
		for (ApplyRequest.CouponInput c : a.coupons()) {
			em.persist(Coupon.of(eventId, a.tenantId(), c.name().strip(), c.description().strip()));
		}
		em.persist(EventBilling.applied(eventId, a.tenantId(), a.hourlyPrice(), a.hostingHourlyPrice(),
				a.estimatedCost()));
		em.persist(EventStatusLog.application(eventId, a.tenantId(), null, ApplicationStatus.APPLYING, now, null));
		return eventId;
	}

	/**
	 * (b) 배너 업로드 성공 — registry · event APPLIED + 배너 경로, 운영자 · 테넌트 알림, 테넌트가 INACTIVE면 ACTIVE.
	 * 이미 APPLYING이 아니면(정리 작업이 먼저 실패 처리) 아무것도 하지 않고 false — 알림도 남기지 않는다.
	 */
	@Transactional
	public boolean markApplied(String tenantId, Long eventId, String bannerPath) {
		if (registries.updateApplicationStatus(eventId, tenantId, ApplicationStatus.APPLYING,
				ApplicationStatus.APPLIED) == 0) {
			return false;
		}
		requireOne(events.markApplied(eventId, tenantId, bannerPath), eventId);
		LocalDateTime now = Kst.now(clock);
		em.persist(EventStatusLog.application(eventId, tenantId, ApplicationStatus.APPLYING, ApplicationStatus.APPLIED,
				now, null));
		notifications.applicationApplied(tenantId, eventId);
		if (tenants.activateIfInactive(tenantId) == 1) {
			em.persist(TenantStatusLog.automatic(tenantId, now, AccountStatus.INACTIVE, AccountStatus.ACTIVE, eventId));
		}
		return true;
	}

	/** (c) 실패 — registry · event APPLY_FAILED, 원인은 진행 기록 · 알림에. 테넌트 상태는 그대로. 이미 처리됐으면 false. */
	@Transactional
	public boolean markFailed(String tenantId, Long eventId, String detail) {
		if (registries.updateApplicationStatus(eventId, tenantId, ApplicationStatus.APPLYING,
				ApplicationStatus.APPLY_FAILED) == 0) {
			return false;
		}
		requireOne(events.markApplyFailed(eventId, tenantId), eventId);
		em.persist(EventStatusLog.application(eventId, tenantId, ApplicationStatus.APPLYING,
				ApplicationStatus.APPLY_FAILED, Kst.now(clock), detail));
		notifications.applicationFailed(tenantId, eventId, detail);
		return true;
	}

	/** registry와 event 상태가 어긋나 있으면 이 트랜잭션 전체를 되돌린다. */
	private static void requireOne(int updated, Long eventId) {
		if (updated != 1) {
			throw new IllegalStateException("registry와 event의 신청 상태가 다릅니다: eventId=" + eventId);
		}
	}

}
