package com.tetra.portal.domain.tenant;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.tetra.portal.domain.StatusType;
import com.tetra.portal.tenancy.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 신청 · 호스팅 · 이벤트 상태 변경 이력(통합). prev/new는 status_type에 따라 값 목록이 달라 문자열로 둔다.
 * changed_by는 운영자 ID(Console 승인 · 반려)이고, 테넌트 · 시스템이 바꾼 행은 NULL이다.
 */
@Entity
@Table(name = "event_status_log")
public class EventStatusLog extends TenantScopedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "log_id", nullable = false)
	private Long logId;

	@Column(name = "event_id", nullable = false, columnDefinition = "int unsigned")
	private Long eventId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status_type", length = 20, nullable = false)
	private StatusType statusType;

	@Column(name = "prev_status", length = 20)
	private String prevStatus;

	@Column(name = "new_status", length = 20, nullable = false)
	private String newStatus;

	@Column(name = "changed_at", nullable = false)
	private LocalDateTime changedAt;

	@Column(name = "detail", length = 500)
	private String detail;

	@Column(name = "changed_by", columnDefinition = "int unsigned")
	private Long changedBy;

	protected EventStatusLog() {
	}

	/** 신청 상태 변경 기록. Portal이 남기는 행은 changed_by가 없다(테넌트 · 시스템). */
	public static EventStatusLog application(Long eventId, String tenantId, Enum<?> prevStatus, Enum<?> newStatus,
			LocalDateTime changedAt, String detail) {
		EventStatusLog log = new EventStatusLog();
		log.eventId = eventId;
		log.tenantId = tenantId;
		log.statusType = StatusType.APPLICATION;
		log.prevStatus = prevStatus == null ? null : prevStatus.name();
		log.newStatus = newStatus.name();
		log.changedAt = changedAt;
		log.detail = detail;
		return log;
	}

	public Long getLogId() {
		return logId;
	}

	public Long getEventId() {
		return eventId;
	}

	public StatusType getStatusType() {
		return statusType;
	}

	public String getPrevStatus() {
		return prevStatus;
	}

	public String getNewStatus() {
		return newStatus;
	}

	public LocalDateTime getChangedAt() {
		return changedAt;
	}

	public String getDetail() {
		return detail;
	}

	public Long getChangedBy() {
		return changedBy;
	}

}
