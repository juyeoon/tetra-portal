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
 * UI-TP-13 알림센터. 행은 Console(승인 INFO · 반려 WARNING)과 운영 자동화가 넣고, Portal은 읽고 읽음 표시만 한다.
 * category 값은 고도화 때 확정(지금 Console은 APPLICATION). priority는 URGENT · WARNING · INFO.
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
