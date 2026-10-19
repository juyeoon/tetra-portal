package com.tetra.portal.domain.tenant;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.tetra.portal.tenancy.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 이벤트 결과 요약(이벤트와 1:1). 집계 Job(발급 API)이 Redis 지표를 INSERT하고, 발급 이력 저장 API가 DB 지표 · CSV 경로를 UPDATE한다.
 * Portal은 읽기만 한다.
 */
@Entity
@Table(name = "event_result_summary")
public class EventResultSummary extends TenantScopedEntity {

	@Id
	@Column(name = "event_id", nullable = false, columnDefinition = "int unsigned")
	private Long eventId;

	@Column(name = "session_count", nullable = false, columnDefinition = "int unsigned")
	private Long sessionCount;

	@Column(name = "ticket_count", nullable = false, columnDefinition = "int unsigned")
	private Long ticketCount;

	@Column(name = "peak_concurrent", nullable = false, columnDefinition = "int unsigned")
	private Long peakConcurrent;

	@Column(name = "peak_at")
	private LocalDateTime peakAt;

	@Column(name = "metrics_finalized_at", nullable = false)
	private LocalDateTime metricsFinalizedAt;

	@Column(name = "total_stock", columnDefinition = "int unsigned")
	private Long totalStock;

	@Column(name = "success_count", columnDefinition = "int unsigned")
	private Long successCount;

	@Column(name = "soldout_count", columnDefinition = "int unsigned")
	private Long soldoutCount;

	@Column(name = "sellout_rate", precision = 5, scale = 4)
	private BigDecimal selloutRate;

	@Column(name = "history_file_path", length = 1024)
	private String historyFilePath;

	@Column(name = "history_generated_at")
	private LocalDateTime historyGeneratedAt;

	protected EventResultSummary() {
	}

	public Long getEventId() {
		return eventId;
	}

	public Long getSessionCount() {
		return sessionCount;
	}

	public Long getTicketCount() {
		return ticketCount;
	}

	public Long getPeakConcurrent() {
		return peakConcurrent;
	}

	public LocalDateTime getPeakAt() {
		return peakAt;
	}

	public Long getSuccessCount() {
		return successCount;
	}

	public Long getSoldoutCount() {
		return soldoutCount;
	}

	public BigDecimal getSelloutRate() {
		return selloutRate;
	}

	public String getHistoryFilePath() {
		return historyFilePath;
	}

	public LocalDateTime getHistoryGeneratedAt() {
		return historyGeneratedAt;
	}

}
