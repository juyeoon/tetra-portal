package com.tetra.portal.domain.platform;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.tetra.portal.domain.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 이벤트 앱 템플릿 — 이벤트 타입당 1행. 신청할 수 있는 타입 = 템플릿이 있는 타입. */
@Entity
@Table(name = "template", catalog = "tetra_platform")
public class Template {

	@Id
	@Column(name = "template_id", nullable = false, columnDefinition = "int unsigned")
	private Long templateId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "event_type", length = 20, nullable = false)
	private EventType eventType;

	@Column(name = "description", length = 500)
	private String description;

	@Column(name = "file_path", length = 1024, nullable = false)
	private String filePath;

	protected Template() {
	}

	public Long getTemplateId() {
		return templateId;
	}

	public EventType getEventType() {
		return eventType;
	}

}
