package com.tetra.portal.notification;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.tetra.portal.common.error.ApiException;
import com.tetra.portal.common.error.ErrorCode;
import com.tetra.portal.common.time.Kst;
import com.tetra.portal.domain.platform.EventRegistry;
import com.tetra.portal.domain.platform.EventRegistryRepository;
import com.tetra.portal.domain.platform.OperatorNotification;
import com.tetra.portal.domain.platform.OperatorNotificationRepository;
import com.tetra.portal.domain.platform.TenantRepository;
import com.tetra.portal.domain.tenant.TenantNotification;
import com.tetra.portal.domain.tenant.TenantNotificationRepository;
import com.tetra.portal.tenancy.TenantContext;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * UI-TP-13 알림센터와, Portal이 남기는 알림(신청 결과)의 유일한 곳. TenantRouter.inTenant 안에서만 부른다.
 * 남기는 규칙 · 문구는 Console(CLAUDE.md §7.13)과 같다 — 상태 변경 트랜잭션 안에서(MANDATORY), 한 사건에 한 번.
 * 운영자 알림은 [테넌트명] [이벤트명], 테넌트 알림은 [이벤트명]부터. 실패는 detail이 있으면 " 원인: {detail}".
 */
@Service
public class NotificationService {

	static final int CONTENT_MAX = 1000;

	enum Category {

		APPLICATION

	}

	enum Priority {

		URGENT, WARNING, INFO

	}

	private final TenantNotificationRepository notifications;

	private final OperatorNotificationRepository operatorNotifications;

	private final TenantRepository tenants;

	private final EventRegistryRepository registries;

	private final Clock clock;

	NotificationService(TenantNotificationRepository notifications,
			OperatorNotificationRepository operatorNotifications, TenantRepository tenants,
			EventRegistryRepository registries, Clock clock) {
		this.notifications = notifications;
		this.operatorNotifications = operatorNotifications;
		this.tenants = tenants;
		this.registries = registries;
		this.clock = clock;
	}

	/** 화면 PortalNotification과 같은 이름. priority는 DB 값(URGENT · WARNING · INFO) — 화면의 CRITICAL = URGENT. */
	public record NotificationItem(Long notificationId, @Schema(description = "관련 이벤트 — 없으면 null") Long eventId,
			@Schema(description = "분류(고도화 때 확정 — 지금은 APPLICATION)") String category,
			@Schema(description = "URGENT · WARNING · INFO") String priority, OffsetDateTime occurredAt, String content,
			boolean read) {
	}

	@Transactional(readOnly = true)
	public List<NotificationItem> list() {
		return notifications.findAllByOrderByOccurredAtDescNotificationIdDesc()
			.stream()
			.map(n -> new NotificationItem(n.getNotificationId(), n.getEventId(), n.getCategory(), n.getPriority(),
					Kst.toOffset(n.getOccurredAt()), n.getContent(), n.isRead()))
			.toList();
	}

	@Transactional
	public void markRead(String tenantId, Long notificationId) {
		if (notifications.markRead(notificationId, tenantId) == 0) {
			throw new ApiException(ErrorCode.NOTIFICATION_NOT_FOUND);
		}
	}

	// ------------------------------------------------------------ 신청 결과 (APPLICATION, WF1-1 b · c)

	/** APPLYING → APPLIED. 운영자 · 테넌트 INFO. 계정 INACTIVE → ACTIVE는 같은 순간이라 따로 남기지 않는다. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void applicationApplied(String tenantId, Long eventId) {
		String eventName = eventName(eventId);
		operator(tenantId, eventId, Category.APPLICATION, Priority.INFO,
				tenantLabel(tenantId) + " [" + eventName + "] 이벤트 신청이 접수되었습니다. 승인 검토가 필요합니다.");
		tenant(tenantId, eventId, Category.APPLICATION, Priority.INFO, "[" + eventName + "] 이벤트 신청이 접수되었습니다.");
	}

	/** APPLYING → APPLY_FAILED(배너 업로드 실패 · 시간 초과). 운영자 · 테넌트 WARNING. 테넌트는 다시 신청하면 된다. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void applicationFailed(String tenantId, Long eventId, String detail) {
		String eventName = eventName(eventId);
		String cause = detail == null ? "" : " 원인: " + detail;
		operator(tenantId, eventId, Category.APPLICATION, Priority.WARNING,
				tenantLabel(tenantId) + " [" + eventName + "] 이벤트 신청 접수에 실패했습니다." + cause);
		tenant(tenantId, eventId, Category.APPLICATION, Priority.WARNING,
				"[" + eventName + "] 이벤트 신청이 접수되지 않았습니다. 다시 신청해 주세요." + cause);
	}

	// ------------------------------------------------------------

	private void operator(String tenantId, Long eventId, Category category, Priority priority, String content) {
		operatorNotifications.save(OperatorNotification.unread(tenantId, eventId, category.name(), priority.name(),
				Kst.now(clock), fit(content)));
	}

	/** tenant_notification은 테넌트 스키마 테이블이라 라우팅된 테넌트와 같아야 한다(이중 확인은 엔티티 리스너가 한다). */
	private void tenant(String tenantId, Long eventId, Category category, Priority priority, String content) {
		if (!tenantId.equals(TenantContext.current())) {
			throw new IllegalStateException("테넌트 알림은 해당 테넌트 스키마 라우팅 안에서만 남길 수 있습니다.");
		}
		notifications.save(TenantNotification.unread(tenantId, eventId, category.name(), priority.name(),
				Kst.now(clock), fit(content)));
	}

	private String tenantLabel(String tenantId) {
		return tenants.findById(tenantId).map(t -> "[" + t.getName() + "]").orElse("[" + tenantId + "]");
	}

	private String eventName(Long eventId) {
		return registries.findById(eventId).map(EventRegistry::getName).orElse(String.valueOf(eventId));
	}

	static String fit(String content) {
		return content.length() <= CONTENT_MAX ? content : content.substring(0, CONTENT_MAX);
	}

}
