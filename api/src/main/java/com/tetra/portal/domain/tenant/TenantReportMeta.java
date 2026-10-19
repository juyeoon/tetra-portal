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
 * UI-TP-12 리포트 목록. 파일은 테넌트 감사 버킷(file_path)에 있고 Portal은 읽기 · 링크만 만든다.
 * TODO(확정 필요, 계획서 Q6): 누가 언제 만드는지(월간 자동 생성 — 운영 자동화 제안), 파일 형식.
 */
@Entity
@Table(name = "tenant_report_meta")
public class TenantReportMeta extends TenantScopedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "report_id", nullable = false)
	private Long reportId;

	@Column(name = "name", length = 100, nullable = false)
	private String name;

	@Column(name = "period_start", nullable = false)
	private LocalDateTime periodStart;

	@Column(name = "period_end", nullable = false)
	private LocalDateTime periodEnd;

	@Column(name = "category", length = 30, nullable = false)
	private String category;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "file_path", length = 1024, nullable = false)
	private String filePath;

	protected TenantReportMeta() {
	}

	public Long getReportId() {
		return reportId;
	}

	public String getName() {
		return name;
	}

	public LocalDateTime getPeriodStart() {
		return periodStart;
	}

	public LocalDateTime getPeriodEnd() {
		return periodEnd;
	}

	public String getCategory() {
		return category;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public String getFilePath() {
		return filePath;
	}

}
