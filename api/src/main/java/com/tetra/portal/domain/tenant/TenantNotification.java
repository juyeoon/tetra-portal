package com.tetra.portal.domain.tenant;

import java.time.LocalDateTime;

import com.tetra.portal.tenancy.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * UI-TP-13 알림센터. 행은 Console(승인 · 반려 · 호스팅 시작 · 입장 시작 · 종료 · 집계 완료)과
 * Portal(신청 접수 INFO · 접수 실패 WARNING)이 넣는다. category는 DB 값 그대로 읽는다(모르는 값도 목록에 나온다 —
 * 값 목록은 NotificationService.Category, 칩은 NotificationRules). priority는 URGENT · WARNING · INFO.
 */
@Entity
@Table(name = "tenant_notification")
public class TenantNotification extends TenantScopedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "notification_id", nullable = false)
	private Long notificationId;

	@Column(name = "event_id", columnDefinition = "int unsigned")
	private Long eventId;

	@Column(name = "category", length = 30, nullable = false)
	private String category;

	@Column(name = "priority", length = 20, nullable = false)
	private String priority;

	@Column(name = "occurred_at", nullable = false)
	private LocalDateTime occurredAt;

	@Column(name = "content", length = 1000, nullable = false)
	private String content;

	@Column(name = "is_read", nullable = false)
	private boolean read;

	protected TenantNotification() {
	}

	public static TenantNotification unread(String tenantId, Long eventId, String category, String priority,
			LocalDateTime occurredAt, String content) {
		TenantNotification n = new TenantNotification();
		n.tenantId = tenantId;
		n.eventId = eventId;
		n.category = category;
		n.priority = priority;
		n.occurredAt = occurredAt;
		n.content = content;
		n.read = false;
		return n;
	}

	public Long getNotificationId() {
		return notificationId;
	}

	public Long getEventId() {
		return eventId;
	}

	public String getCategory() {
		return category;
	}

	public String getPriority() {
		return priority;
	}

	public LocalDateTime getOccurredAt() {
		return occurredAt;
	}

	public String getContent() {
		return content;
	}

	public boolean isRead() {
		return read;
	}

}
