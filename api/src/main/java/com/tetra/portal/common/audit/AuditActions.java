package com.tetra.portal.common.audit;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Portal 감사 허용 목록(2026-11-02 결정) — 테넌트 담당자가 상태를 바꾸거나 파일 · 링크를 받은 행위만. 조회 GET · 문의 메일은 남기지 않는다.
 * 행위는 MVC가 고른 경로 패턴(BEST_MATCHING_PATTERN_ATTRIBUTE) + 메서드로 정한다. 패턴이 없으면(보안 체인에서 401 · 403으로
 * 끝나 MVC까지 오지 않음) 요청 경로를 같은 표에 다시 맞춘다. 경로 · 메서드를 바꾸면 이 표도 같이 바꾼다.
 */
public final class AuditActions {

	/** idFrom 값 — 대상 id = 로그인한 테넌트(JWT sub). */
	public static final String PRINCIPAL = "@principal";

	/**
	 * 표 한 줄. targetType = 대상 종류(없으면 null).
	 * idFrom = 대상 id를 어디서: 경로 변수 이름 · {@link #PRINCIPAL} · null(행위별 보강 값만 — 로그인 · 비밀번호 링크 · 신청).
	 */
	public record Entry(HttpMethod method, String pattern, String action, String targetType, String idFrom) {
	}

	/** 표에서 찾은 줄 + 경로 변수 값. */
	public record Match(Entry entry, Map<String, String> variables) {

		/** idFrom이 경로 변수일 때 그 값. */
		String pathVariable() {
			String from = entry.idFrom();
			return from == null || from.equals(PRINCIPAL) ? null : variables.get(from);
		}

	}

	public static final List<Entry> ENTRIES = List.of(
			new Entry(HttpMethod.POST, "/api/portal/auth/login", "AUTH_LOGIN", AuditContext.TENANT, null),
			new Entry(HttpMethod.POST, "/api/portal/auth/logout", "AUTH_LOGOUT", AuditContext.TENANT, PRINCIPAL),
			new Entry(HttpMethod.POST, "/api/portal/password/setup/verify", "PASSWORD_LINK_VERIFY", AuditContext.TENANT,
					null),
			new Entry(HttpMethod.POST, "/api/portal/password/setup", "PASSWORD_SET", AuditContext.TENANT, null),
			new Entry(HttpMethod.POST, "/api/portal/events", "EVENT_APPLY", AuditContext.EVENT, null),
			new Entry(HttpMethod.PATCH, "/api/portal/settings/email", "SETTINGS_EMAIL_CHANGE", AuditContext.TENANT,
					PRINCIPAL),
			new Entry(HttpMethod.PATCH, "/api/portal/notifications/{notificationId}/read", "NOTIFICATION_READ",
					AuditContext.NOTIFICATION, "notificationId"),
			new Entry(HttpMethod.PATCH, "/api/portal/notifications/read-all", "NOTIFICATION_READ_ALL", null, null),
			new Entry(HttpMethod.GET, "/api/portal/events/{eventId}/result/history-csv", "ISSUANCE_CSV_DOWNLOAD",
					AuditContext.EVENT, "eventId"),
			new Entry(HttpMethod.GET, "/api/portal/reports/{reportId}/download", "REPORT_DOWNLOAD", AuditContext.REPORT,
					"reportId"),
			new Entry(HttpMethod.POST, "/api/portal/reports/{reportId}/email", "REPORT_MAIL", AuditContext.REPORT,
					"reportId"));

	private static final List<Parsed> PARSED = ENTRIES.stream()
		.map(entry -> new Parsed(entry, PathPatternParser.defaultInstance.parse(entry.pattern())))
		.toList();

	private record Parsed(Entry entry, PathPattern pattern) {
	}

	private AuditActions() {
	}

	/** 남길 요청이면 표의 줄, 아니면 empty. */
	public static Optional<Match> match(HttpServletRequest request) {
		String method = request.getMethod();
		Object best = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
		if (best != null) {
			String pattern = best.toString();
			for (Parsed parsed : PARSED) {
				if (parsed.entry().method().matches(method) && parsed.entry().pattern().equals(pattern)) {
					return Optional.of(new Match(parsed.entry(), uriVariables(request)));
				}
			}
			return Optional.empty();
		}
		// MVC까지 오지 않은 요청(보안 체인의 401 · 403) — 요청 경로로 다시 맞춘다
		PathContainer path = PathContainer.parsePath(request.getRequestURI().substring(request.getContextPath().length()));
		for (Parsed parsed : PARSED) {
			if (!parsed.entry().method().matches(method)) {
				continue;
			}
			PathPattern.PathMatchInfo info = parsed.pattern().matchAndExtract(path);
			if (info != null) {
				return Optional.of(new Match(parsed.entry(), info.getUriVariables()));
			}
		}
		return Optional.empty();
	}

	@SuppressWarnings("unchecked")
	private static Map<String, String> uriVariables(HttpServletRequest request) {
		Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
		return variables instanceof Map<?, ?> map ? (Map<String, String>) map : Map.of();
	}

}
