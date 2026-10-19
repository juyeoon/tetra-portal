package com.tetra.portal.notification;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tetra.portal.common.error.ApiException;
import com.tetra.portal.common.error.ErrorCode;
import com.tetra.portal.common.time.Kst;
import com.tetra.portal.domain.tenant.TenantNotificationRepository;

import io.swagger.v3.oas.annotations.media.Schema;

/** UI-TP-13 알림센터. TenantRouter.inTenant 안에서만 부른다. */
@Service
public class NotificationService {

	private final TenantNotificationRepository notifications;

	NotificationService(TenantNotificationRepository notifications) {
		this.notifications = notifications;
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

}
