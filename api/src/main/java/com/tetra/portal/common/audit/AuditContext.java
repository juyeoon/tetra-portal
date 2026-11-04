package com.tetra.portal.common.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import com.tetra.portal.tenancy.TenantIds;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 요청 하나의 감사 값 모음 — AuditFilter가 요청 시작에 요청 속성으로 만들고, 끝날 때 한 줄로 낸다.
 * 컨트롤러 · 서비스는 {@link #current()}로 값만 넣는다. 요청이 아닌 곳(스케줄러 · 배너 업로드 스레드)에서 불리면
 * 아무것도 하지 않는 빈 객체가 돌아오므로 어디서 불러도 안전하다.
 */
public final class AuditContext {

	static final String ATTRIBUTE = AuditContext.class.getName();

	public static final String TENANT = "TENANT";

	public static final String EVENT = "EVENT";

	public static final String NOTIFICATION = "NOTIFICATION";

	public static final String REPORT = "REPORT";

	/** 대상 id · 값 하나의 길이 상한(요청에서 온 값이 그대로 들어갈 수 있는 곳). */
	static final int ID_MAX = 64;

	static final int CHANGE_VALUE_MAX = 200;

	private static final AuditContext NONE = new AuditContext(false);

	private final boolean active;

	private AuditEvent.Party actor;

	private AuditEvent.Party target;

	private String tenantId;

	private Long eventId;

	private String reason;

	private String attempt;

	private final Map<String, AuditEvent.Change> changes = new LinkedHashMap<>();

	private AuditContext(boolean active) {
		this.active = active;
	}

	/** 지금 요청의 감사 값. 요청이 아니거나 감사 대상 경로가 아니면 아무것도 하지 않는 빈 객체. */
	public static AuditContext current() {
		RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
		if (attributes == null) {
			return NONE;
		}
		return attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST) instanceof AuditContext context
				? context : NONE;
	}

	/** 요청 객체를 직접 가진 곳(보안 체인의 오류 응답)에서. */
	public static AuditContext of(HttpServletRequest request) {
		return request.getAttribute(ATTRIBUTE) instanceof AuditContext context ? context : NONE;
	}

	static AuditContext start(HttpServletRequest request) {
		AuditContext context = new AuditContext(true);
		request.setAttribute(ATTRIBUTE, context);
		return context;
	}

	// ------------------------------------------------------------ 행위별 보강

	/** 인증된 테넌트(JWT sub · 로그인 성공 · 검증된 비밀번호 링크) — 행위자이자 tenant_id. */
	public AuditContext tenant(String tenantId) {
		if (active && tenantId != null) {
			this.actor = new AuditEvent.Party(TENANT, limit(tenantId, ID_MAX));
			this.tenantId = limit(tenantId, ID_MAX);
		}
		return this;
	}

	/**
	 * 대상 테넌트 — 로그인 성공 계정, 비밀번호 링크의 t(검증 전이어도). 형식이 맞을 때만 둔다(아무 문자열이나 로그에 들어가지 않게).
	 * 행위자 · tenant_id는 검증된 값으로만 {@link #tenant}.
	 */
	public AuditContext targetTenant(String tenantId) {
		if (active && TenantIds.isValid(tenantId)) {
			this.target = new AuditEvent.Party(TENANT, tenantId);
		}
		return this;
	}

	/** 새로 만든 이벤트(신청 반환 id) — 대상이자 event_id. */
	public AuditContext event(long eventId) {
		if (active) {
			this.target = new AuditEvent.Party(EVENT, Long.toString(eventId));
			this.eventId = eventId;
		}
		return this;
	}

	/** 오류 응답의 code(ErrorCode 이름). 실패한 요청에만 나간다. */
	public AuditContext reason(String code) {
		if (active) {
			this.reason = code;
		}
		return this;
	}

	/** 로그인 실패 — 시도한 이메일을 가려서. 원문은 어디에도 두지 않는다. */
	public AuditContext loginAttempt(String email) {
		if (active) {
			this.attempt = Masking.email(email);
		}
		return this;
	}

	/** 바뀐 필드 하나. 같은 값이면 남기지 않는다. 문자열 값은 200자까지. */
	public AuditContext change(String field, Object before, Object after) {
		if (active && !Objects.equals(before, after)) {
			changes.put(field, new AuditEvent.Change(value(before), value(after)));
		}
		return this;
	}

	// ------------------------------------------------------------ AuditFilter가 읽는 값

	AuditEvent.Party actor() {
		return actor;
	}

	AuditEvent.Party target() {
		return target;
	}

	String tenantId() {
		return tenantId;
	}

	Long eventId() {
		return eventId;
	}

	String reason() {
		return reason;
	}

	String attempt() {
		return attempt;
	}

	Map<String, AuditEvent.Change> changes() {
		return changes.isEmpty() ? null : new LinkedHashMap<>(changes);
	}

	private static Object value(Object value) {
		return value instanceof CharSequence text ? limit(text.toString(), CHANGE_VALUE_MAX) : value;
	}

	/** 코드 포인트 기준으로 자른다(이모지 등 서로게이트 쌍을 반으로 자르지 않게). */
	static String limit(String value, int maxCodePoints) {
		if (value == null || value.codePointCount(0, value.length()) <= maxCodePoints) {
			return value;
		}
		return value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
	}

}
