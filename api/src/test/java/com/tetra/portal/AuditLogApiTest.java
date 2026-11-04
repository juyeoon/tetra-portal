package com.tetra.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.filter.ForwardedHeaderFilter;

import com.tetra.portal.auth.JwtTokenService;
import com.tetra.portal.common.audit.AuditFilter;
import com.tetra.portal.external.DownloadUrlSigner.SignedUrl;
import com.tetra.portal.support.IntegrationTest;
import com.tetra.portal.support.TestData;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 감사 로그(2026-11-02 결정) — 허용 목록 행위마다 stdout에 JSON 한 줄. 출력 캡처로 실제 나간 줄을 파싱해 본다.
 * 값은 모두 가짜(시험용)다.
 */
@ExtendWith(OutputCaptureExtension.class)
class AuditLogApiTest extends IntegrationTest {

	static final JsonMapper JSON = JsonMapper.builder().build();

	static final List<String> KEY_ORDER = List.of("log_type", "audit_id", "time", "app", "actor", "ip", "user_agent",
			"action", "target", "tenant_id", "event_id", "result", "reason", "changes", "truncated", "attempt",
			"request_id");

	static final Pattern TIME = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\+09:00$");

	static final Pattern UUID = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

	static final String PASSWORD = "audit-old-password-1";

	static final String NEW_PASSWORD = "audit-new-password-2";

	static final LocalDateTime OPEN = LocalDateTime.of(2026, 9, 25, 10, 0);

	@Autowired
	FilterRegistrationBean<AuditFilter> auditFilter;

	@Autowired
	ApplicationContext context;

	// ---------------------------------------------------------------- 도우미

	interface Call {

		void run() throws Exception;

	}

	/** 지금까지 나온 감사 줄(원문). 감사 줄은 늘 줄 맨 앞에서 {"log_type":"AUDIT" 로 시작한다. */
	static List<String> auditLines(CapturedOutput output) {
		return output.getOut().lines().filter(line -> line.startsWith("{\"log_type\":\"AUDIT\"")).toList();
	}

	/** call 동안 새로 나온 감사 줄. */
	static List<JsonNode> during(CapturedOutput output, Call call) throws Exception {
		int before = auditLines(output).size();
		call.run();
		List<String> lines = auditLines(output);
		List<JsonNode> parsed = new ArrayList<>();
		for (String line : lines.subList(before, lines.size())) {
			assertThat(line.chars().allMatch(c -> c >= 0x20 && c < 0x7F)).as("ASCII 한 줄: %s", line).isTrue();
			JsonNode node = JSON.readTree(line);
			assertShape(node);
			parsed.add(node);
		}
		return parsed;
	}

	/** call 동안 감사 줄이 정확히 하나. */
	static JsonNode one(CapturedOutput output, Call call) throws Exception {
		List<JsonNode> lines = during(output, call);
		assertThat(lines).hasSize(1);
		return lines.get(0);
	}

	/** 모든 줄 공통: 키 순서 · 고정 값 · 형식. actor · target · tenant_id는 없어도 null로 있다. */
	static void assertShape(JsonNode line) {
		List<String> keys = List.copyOf(line.propertyNames());
		assertThat(KEY_ORDER).containsSubsequence(keys);
		assertThat(keys).contains("log_type", "audit_id", "time", "app", "actor", "ip", "action", "target", "tenant_id",
				"result", "request_id");
		assertThat(line.path("log_type").asString()).isEqualTo("AUDIT");
		assertThat(line.path("app").asString()).isEqualTo("portal");
		assertThat(line.path("audit_id").asString()).matches(UUID);
		assertThat(line.path("time").asString()).matches(TIME);
		assertThat(line.path("result").asString()).isIn("SUCCESS", "FAILURE");
		assertThat(keys).doesNotContain("password", "sig", "token", "cookie", "body", "stack", "q");
	}

	static void assertTenantLine(JsonNode line, String action, String tenantId) {
		assertThat(line.path("action").asString()).isEqualTo(action);
		assertThat(line.path("result").asString()).isEqualTo("SUCCESS");
		assertThat(line.has("reason")).isFalse();
		assertThat(line.path("actor").path("type").asString()).isEqualTo("TENANT");
		assertThat(line.path("actor").path("id").asString()).isEqualTo(tenantId);
		assertThat(line.path("tenant_id").asString()).isEqualTo(tenantId);
	}

	static void assertTarget(JsonNode line, String type, Object id) {
		assertThat(line.path("target").path("type").asString()).isEqualTo(type);
		assertThat(line.path("target").path("id").asString()).isEqualTo(String.valueOf(id));
	}

	private ResultActions login(String email, String password) throws Exception {
		return mvc.perform(post("/api/portal/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
	}

	private static MockHttpServletRequestBuilder linkRequest(String path, String t, long exp, String sig,
			String password) {
		String body = password == null ? "{\"t\":\"%s\",\"exp\":%d,\"sig\":\"%s\"}".formatted(t, exp, sig)
				: "{\"t\":\"%s\",\"exp\":%d,\"sig\":\"%s\",\"password\":\"%s\"}".formatted(t, exp, sig, password);
		return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	/** 결과가 READY인 이벤트 1003 (발급 이력 CSV를 받을 수 있음). */
	private void readyEvent(String tenantId, String historyPath) {
		data.event(1003, tenantId).approved("ENDED", "EXPIRED", "chsk2609").insert();
		data.summaryAggregated(tenantId, 1003, 1200, 1000, 900, OPEN.plusMinutes(1));
		data.summaryHistory(tenantId, 1003, 10000, 700, 200, "0.0700", historyPath, OPEN.plusHours(3));
	}

	private static MockHttpServletRequestBuilder logout() {
		return post("/api/portal/auth/logout");
	}

	// ---------------------------------------------------------------- 허용 목록

	@Test
	void everyAllowedActionWritesOneLineWithItsFields(CapturedOutput output) throws Exception {
		data.tenantWithPassword(B, "B사", PASSWORD, "ACTIVE", "READY");
		readyEvent(B, "issuance-history/1003.csv");
		long notification = data.notification(B, 1003L, "INFO", TestData.BASE, "신청이 승인되었습니다.", false);
		long report = data.report(B, "9월 리포트", "reports/2026-09.pdf", TestData.BASE);
		when(urlSigner.presignGet(anyString(), anyString(), any(), anyString()))
			.thenReturn(new SignedUrl("https://signed.example/file", Instant.now().plusSeconds(300)));
		long exp = Instant.now().plus(Duration.ofHours(24)).getEpochSecond();
		String sig = PasswordSetupApiTest.consoleSign(B, exp, data.passwordHash(B));
		Cookie cookie = loginCookie(B);

		JsonNode verify = one(output, () -> mvc
			.perform(linkRequest("/api/portal/password/setup/verify", B, exp, sig, null))
			.andExpect(status().isOk()));
		assertTenantLine(verify, "PASSWORD_LINK_VERIFY", B);
		assertTarget(verify, "TENANT", B);

		JsonNode set = one(output, () -> mvc.perform(linkRequest("/api/portal/password/setup", B, exp, sig, NEW_PASSWORD))
			.andExpect(status().isNoContent()));
		assertTenantLine(set, "PASSWORD_SET", B);
		assertTarget(set, "TENANT", B);

		String[] setCookie = new String[1];
		JsonNode login = one(output, () -> setCookie[0] = login(TestData.email(B), NEW_PASSWORD)
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getHeader(HttpHeaders.SET_COOKIE));
		assertTenantLine(login, "AUTH_LOGIN", B);
		assertTarget(login, "TENANT", B);
		assertThat(login.has("attempt")).isFalse();
		String issuedToken = setCookie[0].substring(setCookie[0].indexOf('=') + 1, setCookie[0].indexOf(';'));

		JsonNode logout = one(output, () -> mvc
			.perform(logout().cookie(new Cookie(JwtTokenService.COOKIE_NAME, issuedToken)))
			.andExpect(status().isNoContent()));
		assertTenantLine(logout, "AUTH_LOGOUT", B);
		assertTarget(logout, "TENANT", B);

		long[] applied = new long[1];
		JsonNode apply = one(output, () -> {
			String body = mvc.perform(multipart("/api/portal/events")
				.file(new MockMultipartFile("application", "", MediaType.APPLICATION_JSON_VALUE,
						ApplyApiTest.application(Map.of()).getBytes()))
				.file(new MockMultipartFile("banner", "banner.png", "image/png", ApplyApiTest.PNG))
				.cookie(cookie)).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
			applied[0] = Long.parseLong(body.replaceAll(".*\"eventId\":(\\d+).*", "$1"));
		});
		assertThat(data.awaitApplicationStatus(applied[0])).isEqualTo("APPLIED");
		assertTenantLine(apply, "EVENT_APPLY", B);
		assertTarget(apply, "EVENT", applied[0]);
		assertThat(apply.path("event_id").asLong()).isEqualTo(applied[0]);

		JsonNode settings = one(output, () -> mvc.perform(patch("/api/portal/settings/email").cookie(cookie)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"enabled\":true}")).andExpect(status().isOk()));
		assertTenantLine(settings, "SETTINGS_EMAIL_CHANGE", B);
		assertTarget(settings, "TENANT", B);
		JsonNode change = settings.path("changes").path("email_notification_enabled");
		assertThat(change.path("before").isBoolean()).isTrue();
		assertThat(change.path("before").asBoolean()).isFalse();
		assertThat(change.path("after").asBoolean()).isTrue();
		assertThat(List.copyOf(settings.path("changes").propertyNames())).containsExactly("email_notification_enabled");

		// 같은 값으로 다시 — 행위는 남지만 바뀐 필드가 없으니 changes 없음
		JsonNode unchanged = one(output, () -> mvc.perform(patch("/api/portal/settings/email").cookie(cookie)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"enabled\":true}")).andExpect(status().isOk()));
		assertThat(unchanged.path("action").asString()).isEqualTo("SETTINGS_EMAIL_CHANGE");
		assertThat(unchanged.has("changes")).isFalse();

		JsonNode read = one(output, () -> mvc
			.perform(patch("/api/portal/notifications/{id}/read", notification).cookie(cookie))
			.andExpect(status().isNoContent()));
		assertTenantLine(read, "NOTIFICATION_READ", B);
		assertTarget(read, "NOTIFICATION", notification);

		JsonNode readAll = one(output, () -> mvc.perform(patch("/api/portal/notifications/read-all").cookie(cookie))
			.andExpect(status().isNoContent()));
		assertTenantLine(readAll, "NOTIFICATION_READ_ALL", B);
		assertThat(readAll.path("target").isNull()).isTrue();

		JsonNode csv = one(output, () -> mvc
			.perform(get("/api/portal/events/{id}/result/history-csv", 1003).cookie(cookie))
			.andExpect(status().isOk()));
		assertTenantLine(csv, "ISSUANCE_CSV_DOWNLOAD", B);
		assertTarget(csv, "EVENT", 1003);
		assertThat(csv.path("event_id").asLong()).isEqualTo(1003);

		JsonNode download = one(output, () -> mvc
			.perform(get("/api/portal/reports/{id}/download", report).cookie(cookie))
			.andExpect(status().isOk()));
		assertTenantLine(download, "REPORT_DOWNLOAD", B);
		assertTarget(download, "REPORT", report);

		JsonNode mail = one(output, () -> mvc.perform(post("/api/portal/reports/{id}/email", report).cookie(cookie))
			.andExpect(status().isNoContent()));
		assertTenantLine(mail, "REPORT_MAIL", B);
		assertTarget(mail, "REPORT", report);

		// 비밀 값은 감사 줄 · 일반 로그 어디에도 없다 — 링크 서명 · 비밀번호 · 발급 토큰 · 쿠키 · 담당자 이메일 원문
		assertThat(output.getAll()).doesNotContain(sig)
			.doesNotContain(PASSWORD)
			.doesNotContain(NEW_PASSWORD)
			.doesNotContain(issuedToken)
			.doesNotContain(cookie.getValue())
			.doesNotContain(TestData.email(B))
			.doesNotContain("signed.example");
	}

	@Test
	void readsSupportMailAndHealthAreNotAudited(CapturedOutput output) throws Exception {
		data.tenant(A, "A사", "ACTIVE", "READY");
		readyEvent(A, "issuance-history/1003.csv");
		Cookie cookie = loginCookie(A);

		List<JsonNode> lines = during(output, () -> {
			for (String path : List.of("/api/portal/me", "/api/portal/plans", "/api/portal/events",
					"/api/portal/events/1003", "/api/portal/events/1003/result", "/api/portal/notifications?q=audit-search-word",
					"/api/portal/notifications/unread-count", "/api/portal/reports", "/api/portal/settings")) {
				mvc.perform(get(path).cookie(cookie)).andExpect(status().isOk());
			}
			mvc.perform(post("/api/portal/support-mail").contentType(MediaType.APPLICATION_JSON)
				.content("{\"from\":\"someone@example.com\",\"subject\":\"문의\",\"body\":\"본문\"}"))
				.andExpect(status().isNoContent());
			mvc.perform(get("/actuator/health")).andExpect(status().isOk());
			// 허용 목록 경로라도 다른 메서드면 남기지 않는다
			mvc.perform(get("/api/portal/auth/login").cookie(cookie)).andExpect(status().isMethodNotAllowed());
		});
		assertThat(lines).isEmpty();
		assertThat(output.getAll()).doesNotContain("audit-search-word").doesNotContain("someone@example.com");
	}

	// ---------------------------------------------------------------- 실패

	@Test
	void loginFailureKeepsOnlyMaskedAttempt(CapturedOutput output) throws Exception {
		data.tenantWithPassword(A, "A사", PASSWORD, "ACTIVE", "READY");

		JsonNode unknown = one(output, () -> login("kim.audit@example.com", "wrong-password-xyz")
			.andExpect(status().isUnauthorized()));
		assertThat(unknown.path("action").asString()).isEqualTo("AUTH_LOGIN");
		assertThat(unknown.path("result").asString()).isEqualTo("FAILURE");
		assertThat(unknown.path("reason").asString()).isEqualTo("LOGIN_FAILED");
		assertThat(unknown.path("attempt").asString()).isEqualTo("ki***@ex***.com");
		assertThat(unknown.path("actor").isNull()).isTrue();
		assertThat(unknown.path("target").isNull()).isTrue();
		assertThat(unknown.path("tenant_id").isNull()).isTrue();

		JsonNode wrongPassword = one(output, () -> login(TestData.email(A), "wrong-password-xyz")
			.andExpect(status().isUnauthorized()));
		assertThat(wrongPassword.path("attempt").asString()).isEqualTo("ad***@aa***.com");
		assertThat(wrongPassword.path("tenant_id").isNull()).isTrue();

		// 다른 테넌트 쿠키를 든 채 실패 — 행위자는 그 쿠키의 테넌트, 대상은 없음(시도한 계정은 attempt로만)
		JsonNode withCookie = one(output, () -> mvc.perform(post("/api/portal/auth/login").cookie(loginCookie(B))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"kim.audit@example.com\",\"password\":\"wrong-password-xyz\"}"))
			.andExpect(status().isUnauthorized()));
		assertThat(withCookie.path("actor").path("id").asString()).isEqualTo(B);
		assertThat(withCookie.path("tenant_id").asString()).isEqualTo(B);
		assertThat(withCookie.path("target").isNull()).isTrue();
		assertThat(withCookie.path("attempt").asString()).isEqualTo("ki***@ex***.com");

		// 검사에서 끝난 실패는 계정을 보지 않았으므로 attempt 없음
		JsonNode invalid = one(output, () -> mvc.perform(post("/api/portal/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"\"}")).andExpect(status().isBadRequest()));
		assertThat(invalid.path("reason").asString()).isEqualTo("INVALID_INPUT");
		assertThat(invalid.has("attempt")).isFalse();

		assertThat(output.getAll()).doesNotContain("kim.audit@example.com")
			.doesNotContain(TestData.email(A))
			.doesNotContain("wrong-password-xyz")
			.doesNotContain(PASSWORD);
	}

	@Test
	void passwordLinkFailuresKeepWellFormedTargetOnly(CapturedOutput output) throws Exception {
		data.tenant(A, "A사", "INACTIVE", "READY");
		long exp = Instant.now().plus(Duration.ofHours(24)).getEpochSecond();
		String sig = PasswordSetupApiTest.consoleSign(A, exp, null);

		JsonNode forged = one(output, () -> mvc
			.perform(linkRequest("/api/portal/password/setup/verify", A, exp, sig.substring(1) + "A", null))
			.andExpect(status().isGone()));
		assertThat(forged.path("action").asString()).isEqualTo("PASSWORD_LINK_VERIFY");
		assertThat(forged.path("result").asString()).isEqualTo("FAILURE");
		assertThat(forged.path("reason").asString()).isEqualTo("PASSWORD_LINK_INVALID");
		assertTarget(forged, "TENANT", A);
		assertThat(forged.path("actor").isNull()).isTrue();
		assertThat(forged.path("tenant_id").isNull()).isTrue();

		JsonNode malformed = one(output, () -> mvc
			.perform(linkRequest("/api/portal/password/setup", "NOT-A-TENANT\\n", exp, sig, NEW_PASSWORD)) // t 값에 줄바꿈
			.andExpect(status().isGone()));
		assertThat(malformed.path("action").asString()).isEqualTo("PASSWORD_SET");
		assertThat(malformed.path("target").isNull()).isTrue();
		assertThat(output.getAll()).doesNotContain(sig).doesNotContain(NEW_PASSWORD).doesNotContain("NOT-A-TENANT");
	}

	@Test
	void securityChainRejectionsAreAuditedWithoutActor(CapturedOutput output) throws Exception {
		JsonNode noCookie = one(output, () -> mvc.perform(patch("/api/portal/settings/email")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"enabled\":true}")).andExpect(status().isUnauthorized()));
		assertThat(noCookie.path("action").asString()).isEqualTo("SETTINGS_EMAIL_CHANGE");
		assertThat(noCookie.path("result").asString()).isEqualTo("FAILURE");
		assertThat(noCookie.path("reason").asString()).isEqualTo("UNAUTHENTICATED");
		assertThat(noCookie.path("actor").isNull()).isTrue();
		assertThat(noCookie.path("tenant_id").isNull()).isTrue();
		assertThat(noCookie.path("target").isNull()).isTrue();

		// 경로 변수 대상은 MVC까지 오지 않아도 경로에서 채운다
		JsonNode csv = one(output, () -> mvc.perform(get("/api/portal/events/{id}/result/history-csv", 1003))
			.andExpect(status().isUnauthorized()));
		assertThat(csv.path("action").asString()).isEqualTo("ISSUANCE_CSV_DOWNLOAD");
		assertTarget(csv, "EVENT", 1003);
		assertThat(csv.path("event_id").asLong()).isEqualTo(1003);
		assertThat(csv.path("actor").isNull()).isTrue();

		// 공개 경로라도 망가진 쿠키는 보안 체인이 401
		JsonNode broken = one(output, () -> mvc
			.perform(logout().cookie(new Cookie(JwtTokenService.COOKIE_NAME, "garbage-token-value")))
			.andExpect(status().isUnauthorized()));
		assertThat(broken.path("action").asString()).isEqualTo("AUTH_LOGOUT");
		assertThat(broken.path("reason").asString()).isEqualTo("UNAUTHENTICATED");
		assertThat(broken.path("actor").isNull()).isTrue();

		// 403 — 인증 객체는 있으나 인증되지 않음 → accessDeniedHandler
		JsonNode forbidden = one(output, () -> mvc.perform(patch("/api/portal/notifications/read-all")
			.with(authentication(new TestingAuthenticationToken("someone", null)))).andExpect(status().isForbidden()));
		assertThat(forbidden.path("action").asString()).isEqualTo("NOTIFICATION_READ_ALL");
		assertThat(forbidden.path("result").asString()).isEqualTo("FAILURE");
		assertThat(forbidden.path("reason").asString()).isEqualTo("FORBIDDEN");
		assertThat(forbidden.path("actor").isNull()).isTrue();
		assertThat(output.getAll()).doesNotContain("garbage-token-value");
	}

	@Test
	void exceptionEscapingTheFilterIsUnhandledFailure(CapturedOutput output) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/portal/auth/logout");
		JsonNode line = one(output, () -> assertThatThrownBy(() -> auditFilter.getFilter()
			.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
				throw new IllegalStateException("boom");
			})).isInstanceOf(IllegalStateException.class));
		assertThat(line.path("action").asString()).isEqualTo("AUTH_LOGOUT");
		assertThat(line.path("result").asString()).isEqualTo("FAILURE");
		assertThat(line.path("reason").asString()).isEqualTo("UNHANDLED");
	}

	// ---------------------------------------------------------------- ip · request_id

	@Test
	void ipIsRightMostForwardedForValueAddedByAlb(CapturedOutput output) throws Exception {
		JsonNode forwarded = one(output, () -> mvc.perform(logout().header("X-Forwarded-For", "1.1.1.1, 10.0.0.5")));
		assertThat(forwarded.path("ip").asString()).isEqualTo("10.0.0.5");

		// 클라이언트가 보낸 값(왼쪽)은 쓰지 않는다 — ALB가 끝에 실제 주소를 붙인다
		JsonNode spoofed = one(output, () -> mvc
			.perform(logout().header("X-Forwarded-For", "6.6.6.6, 7.7.7.7,203.0.113.7")));
		assertThat(spoofed.path("ip").asString()).isEqualTo("203.0.113.7");

		JsonNode direct = one(output, () -> mvc.perform(logout().with(request -> {
			request.setRemoteAddr("192.0.2.10");
			return request;
		})));
		assertThat(direct.path("ip").asString()).isEqualTo("192.0.2.10");

		// framework 전략(ForwardedHeaderFilter)이 꺼져 있다 — 켜지면 맨 왼쪽 값으로 remoteAddr를 바꾸고 헤더를 지운다
		assertThat(context.getEnvironment().getProperty("server.forward-headers-strategy")).isEqualTo("none");
		assertThat(context.getBeansOfType(ForwardedHeaderFilter.class)).isEmpty();
		assertThat(context.getBeansOfType(FilterRegistrationBean.class).values())
			.noneMatch(registration -> registration.getFilter() instanceof ForwardedHeaderFilter);
	}

	@Test
	void requestIdComesFromAlbTraceHeader(CapturedOutput output) throws Exception {
		String root = "1-67891233-abcdef012345678912345678";
		String self = "1-67891234-12456789abcdef0123456789";

		JsonNode both = one(output, () -> mvc.perform(logout().header("X-Amzn-Trace-Id",
				"Self=" + self + ";Root=" + root + ";Sampled=1")));
		assertThat(both.path("request_id").asString()).isEqualTo(self);

		JsonNode rootOnly = one(output, () -> mvc.perform(logout().header("X-Amzn-Trace-Id", "Root=" + root)));
		assertThat(rootOnly.path("request_id").asString()).isEqualTo(root);

		JsonNode malformed = one(output, () -> mvc.perform(logout().header("X-Amzn-Trace-Id", "Root=1-zzzz-not-a-trace")));
		assertThat(malformed.path("request_id").asString()).matches(UUID);

		JsonNode missing = one(output, () -> mvc.perform(logout()));
		assertThat(missing.path("request_id").asString()).matches(UUID);
		assertThat(missing.path("audit_id").asString()).isNotEqualTo(missing.path("request_id").asString());
	}

	// ---------------------------------------------------------------- 한 줄 · 형식

	@Test
	void lineStaysOneAsciiLineWhateverTheValues(CapturedOutput output) throws Exception {
		String userAgent = "Mozilla/5.0 (테스트 브라우저)\n{\"log_type\":\"AUDIT\",\"forged\":true}\r\u0001 끝";
		JsonNode line = one(output, () -> mvc.perform(logout().header(HttpHeaders.USER_AGENT, userAgent)));
		assertThat(line.path("user_agent").asString()).isEqualTo(userAgent);
		// 값 안의 줄바꿈으로 가짜 감사 줄을 만들 수 없다
		assertThat(output.getOut().lines()).noneMatch(l -> l.startsWith("{\"log_type\":\"AUDIT\",\"forged\""));
		String raw = auditLines(output).get(auditLines(output).size() - 1);
		assertThat(raw).contains("\\n").contains("\\r").contains("\\u0001").doesNotContain("테스트");
		assertThat(raw.toLowerCase()).contains("\\ud14c").contains("\\u2028"); // 테 · 줄 구분자(U+2028)도 이스케이프

		JsonNode longAgent = one(output, () -> mvc.perform(logout().header(HttpHeaders.USER_AGENT, "a".repeat(5000))));
		assertThat(longAgent.path("user_agent").asString()).hasSize(512);

		// JSON 본문 안의 \n → 이메일 값에 실제 줄바꿈
		JsonNode korean = one(output, () -> login("김\\n철수@예시.한국", "아무 비밀번호").andExpect(status().isUnauthorized()));
		assertThat(korean.path("attempt").asString()).isEqualTo("김\n***@예***.한국");
		assertThat(output.getAll()).doesNotContain("아무 비밀번호");
	}

	/** 일반 로그는 Boot 기본 콘솔 형식 그대로 — MDC request_id도 찍히지 않는다. 감사 줄은 그 형식으로 한 번 더 나가지 않는다. */
	@Test
	void generalLogFormatIsUnchanged(CapturedOutput output) throws Exception {
		data.tenant(A, "A사", "ACTIVE", "READY");
		data.tenant(B, "B사", "ACTIVE", "READY");
		readyEvent(A, "s3://tetra-audit-logs-test-" + B + "/issuance-history/2001.csv"); // 다른 테넌트 버킷 → 500 + 오류 로그
		String root = "1-6789abcd-0123456789abcdef01234567";

		JsonNode line = one(output, () -> mvc.perform(get("/api/portal/events/{id}/result/history-csv", 1003)
			.cookie(loginCookie(A))
			.header("X-Amzn-Trace-Id", "Root=" + root)).andExpect(status().isInternalServerError()));
		assertThat(line.path("reason").asString()).isEqualTo("INTERNAL_ERROR");
		assertThat(line.path("request_id").asString()).isEqualTo(root);

		Pattern bootConsole = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}[+-]\\d{2}:\\d{2} +ERROR \\d+ --- "
				+ "\\[tetra-portal-api\\] \\[.{15}\\] .{40} : 파일 경로가 다른 버킷을 가리킵니다: .*$");
		List<String> errorLines = output.getOut()
			.lines()
			.map(l -> l.replaceAll("\u001B\\[[;\\d]*m", ""))
			.filter(l -> l.contains("파일 경로가 다른 버킷을 가리킵니다"))
			.toList();
		assertThat(errorLines).hasSize(1);
		assertThat(errorLines.get(0)).matches(bootConsole).doesNotContain(root);
		assertThat(output.getOut().lines().filter(l -> l.contains("\"log_type\":\"AUDIT\"")))
			.allMatch(l -> l.startsWith("{\"log_type\":\"AUDIT\""));
	}

}
