package com.tetra.portal.domain.platform;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.tetra.portal.domain.ApplicationStatus;
import com.tetra.portal.domain.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 전역 event_id 발급 · 라우팅 · 운영 콘솔 목록용 복제 컬럼(tetra_platform).
 * Portal은 신청(WF1-1)에서 행을 만들어 event_id를 받고, APPLYING → APPLIED / APPLY_FAILED만 바꾼다.
 * 같은 트랜잭션에서 registry를 먼저, 테넌트 스키마 event를 뒤에 바꾼다(Console과 같은 순서).
 * 테넌트 화면 조회에는 쓰지 않는다(논리 ERD).
 */
@Entity
@Table(name = "event_registry", catalog = "tetra_platform")
public class EventRegistry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "event_id", nullable = false, columnDefinition = "int unsigned")
	private Long eventId;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "tenant_id", length = 12, nullable = false, updatable = false)
	private String tenantId;

	@Column(name = "name", length = 100, nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "event_type", length = 20, nullable = false)
	private EventType eventType;

	@Column(name = "traffic_plan_code", length = 10, nullable = false)
	private String trafficPlanCode;

	@Column(name = "hosting_start_at", nullable = false)
	private LocalDateTime hostingStartAt;

	@Column(name = "start_at", nullable = false)
	private LocalDateTime startAt;

	@Column(name = "applied_at", nullable = false)
	private LocalDateTime appliedAt;

	@Column(name = "processed_at")
	private LocalDateTime processedAt;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "application_status", length = 20, nullable = false)
	private ApplicationStatus applicationStatus;

	@Column(name = "event_status", length = 20)
	private String eventStatus;

	@Column(name = "hosting_status", length = 20)
	private String hostingStatus;

	protected EventRegistry() {
	}

	/** 신청 접수 — APPLYING. event_id는 INSERT 뒤에 정해진다. */
	public static EventRegistry applying(String tenantId, String name, EventType eventType, String trafficPlanCode,
			LocalDateTime hostingStartAt, LocalDateTime startAt, LocalDateTime appliedAt) {
		EventRegistry r = new EventRegistry();
		r.tenantId = tenantId;
		r.name = name;
		r.eventType = eventType;
		r.trafficPlanCode = trafficPlanCode;
		r.hostingStartAt = hostingStartAt;
		r.startAt = startAt;
		r.appliedAt = appliedAt;
		r.applicationStatus = ApplicationStatus.APPLYING;
		return r;
	}

	public Long getEventId() {
		return eventId;
	}

	public String getTenantId() {
		return tenantId;
	}

	public LocalDateTime getAppliedAt() {
		return appliedAt;
	}

	public ApplicationStatus getApplicationStatus() {
		return applicationStatus;
	}

}
