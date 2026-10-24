package com.tetra.portal.domain.platform;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 운영 콘솔 알림센터(OPS-08). Portal은 신청 결과(APPLIED · APPLY_FAILED) 알림만 넣는다 — 읽기 · 읽음 표시는 Console. */
@Entity
@Table(name = "operator_notification", catalog = "tetra_platform")
public class OperatorNotification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "notification_id", nullable = false)
	private Long notificationId;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "tenant_id", length = 12)
	private String tenantId;

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

	protected OperatorNotification() {
	}

	public static OperatorNotification unread(String tenantId, Long eventId, String category, String priority,
			LocalDateTime occurredAt, String content) {
		OperatorNotification n = new OperatorNotification();
		n.tenantId = tenantId;
		n.eventId = eventId;
		n.category = category;
		n.priority = priority;
		n.occurredAt = occurredAt;
		n.content = content;
		n.read = false;
		return n;
	}

}
