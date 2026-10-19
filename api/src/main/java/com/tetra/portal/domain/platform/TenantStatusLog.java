package com.tetra.portal.domain.platform;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.tetra.portal.domain.AccountStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 테넌트 계정 상태 이력. Portal은 첫 신청 성공 때 INACTIVE → ACTIVE 자동 활성화만 남긴다(related_event_id). */
@Entity
@Table(name = "tenant_status_log", catalog = "tetra_platform")
public class TenantStatusLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "log_id", nullable = false)
	private Long logId;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "tenant_id", length = 12, nullable = false)
	private String tenantId;

	@Column(name = "changed_at", nullable = false)
	private LocalDateTime changedAt;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "prev_status", length = 20)
	private AccountStatus prevStatus;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "new_status", length = 20, nullable = false)
	private AccountStatus newStatus;

	@Column(name = "reason", length = 500)
	private String reason;

	@Column(name = "related_event_id", columnDefinition = "int unsigned")
	private Long relatedEventId;

	protected TenantStatusLog() {
	}

	/** 자동 처리(reason 없음, 연관 이벤트 있음). */
	public static TenantStatusLog automatic(String tenantId, LocalDateTime changedAt, AccountStatus prevStatus,
			AccountStatus newStatus, Long relatedEventId) {
		TenantStatusLog log = new TenantStatusLog();
		log.tenantId = tenantId;
		log.changedAt = changedAt;
		log.prevStatus = prevStatus;
		log.newStatus = newStatus;
		log.relatedEventId = relatedEventId;
		return log;
	}

}
