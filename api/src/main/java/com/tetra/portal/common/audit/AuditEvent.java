package com.tetra.portal.common.audit;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * 감사 로그 한 건(stdout JSON 한 줄). 키 이름 · 순서는 인프라(구독 필터 {@code $.log_type = "AUDIT"} → Firehose →
 * S3 audit-logs/tenant_id=…/)와 Console이 같이 쓰는 약속이라 바꾸면 같이 바꾼다.
 * actor · target · tenant_id는 없어도 null로 남기고(형식 고정), 나머지는 값이 있을 때만 나간다.
 * 비밀번호 · 토큰 · 쿠키 · 링크 서명 · 요청 본문 · 스택 · 검색어는 넣지 않는다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "log_type", "audit_id", "time", "app", "actor", "ip", "user_agent", "action", "target", "tenant_id",
		"event_id", "result", "reason", "changes", "truncated", "attempt", "request_id" })
public record AuditEvent(
		@JsonProperty("log_type") String logType,
		@JsonProperty("audit_id") String auditId,
		@JsonProperty("time") String time,
		@JsonProperty("app") String app,
		@JsonProperty("actor") @JsonInclude(JsonInclude.Include.ALWAYS) Party actor,
		@JsonProperty("ip") String ip,
		@JsonProperty("user_agent") String userAgent,
		@JsonProperty("action") String action,
		@JsonProperty("target") @JsonInclude(JsonInclude.Include.ALWAYS) Party target,
		@JsonProperty("tenant_id") @JsonInclude(JsonInclude.Include.ALWAYS) String tenantId,
		@JsonProperty("event_id") Long eventId,
		@JsonProperty("result") String result,
		@JsonProperty("reason") String reason,
		@JsonProperty("changes") Map<String, Change> changes,
		@JsonProperty("truncated") Boolean truncated,
		@JsonProperty("attempt") String attempt,
		@JsonProperty("request_id") String requestId) {

	public static final String LOG_TYPE = "AUDIT";

	public static final String APP = "portal";

	public static final String SUCCESS = "SUCCESS";

	public static final String FAILURE = "FAILURE";

	/** 행위자 · 대상. id는 문자열(테넌트 id가 문자열이라 종류와 상관없이 맞춘다). */
	@JsonPropertyOrder({ "type", "id" })
	public record Party(@JsonProperty("type") String type, @JsonProperty("id") String id) {
	}

	/** 바뀐 필드 하나. before · after가 null이어도 그대로 남긴다(없던 값 → 생긴 값). */
	@JsonPropertyOrder({ "before", "after" })
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record Change(@JsonProperty("before") Object before, @JsonProperty("after") Object after) {
	}

	/** 줄이 상한을 넘을 때 — changes를 비우고 truncated:true. */
	AuditEvent withoutChanges() {
		return new AuditEvent(logType, auditId, time, app, actor, ip, userAgent, action, target, tenantId, eventId,
				result, reason, null, Boolean.TRUE, attempt, requestId);
	}

}
