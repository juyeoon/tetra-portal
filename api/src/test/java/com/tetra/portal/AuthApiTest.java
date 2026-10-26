package com.tetra.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.tetra.portal.auth.JwtTokenService;
import com.tetra.portal.config.TetraProperties;
import com.tetra.portal.support.IntegrationTest;
import com.tetra.portal.support.TestData;

import jakarta.servlet.http.Cookie;

/** UI-TP-01/02 로그인 · 로그아웃 · me (계획서 4-1). */
class AuthApiTest extends IntegrationTest {

	static final String PASSWORD = "portal-password-1";

	@Autowired
	TetraProperties properties;

	private ResultActions login(String email, String password) throws Exception {
		return mvc.perform(post("/api/portal/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
	}

	@Test
	void loginSetsSecureCookieAndReturnsAccount() throws Exception {
		data.tenantWithPassword(A, "A사", PASSWORD, "ACTIVE", "READY");

		String setCookie = login(TestData.email(A), PASSWORD).andExpect(status().isOk())
			.andExpect(jsonPath("$.tenantId").value(A))
			.andExpect(jsonPath("$.companyName").value("A사"))
			.andExpect(jsonPath("$.managerName").value("A사 담당자"))
			.andExpect(jsonPath("$.email").value(TestData.email(A)))
			.andReturn()
			.getResponse()
			.getHeader(HttpHeaders.SET_COOKIE);

		assertThat(setCookie).startsWith(JwtTokenService.COOKIE_NAME + "=")
			.contains("HttpOnly")
			.contains("Secure")
			.contains("SameSite=Strict")
			.contains("Path=/api/portal")
			.contains("Max-Age=28800");

		String token = setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
		mvc.perform(get("/api/portal/me").cookie(new Cookie(JwtTokenService.COOKIE_NAME, token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tenantId").value(A));
	}

	@Test
	void inactiveTenantCanLogIn() throws Exception {
		// 첫 신청 전 테넌트는 INACTIVE — 로그인해야 신청할 수 있다
		data.tenantWithPassword(A, "A사", PASSWORD, "INACTIVE", "READY");
		login(TestData.email(A), PASSWORD).andExpect(status().isOk());
	}

	@Test
	void everyLoginFailureLooksTheSame() throws Exception {
		data.tenantWithPassword(A, "A사", PASSWORD, "ACTIVE", "READY");
		data.tenant(B, "B사", "INACTIVE", "READY"); // 비밀번호 설정 전
		data.tenantWithPassword("cccccccccccc", "C사", PASSWORD, "INACTIVE", "PROVISIONING"); // 준비 중

		String[][] cases = { { TestData.email(A), "wrong-password" }, { "nobody@example.com", PASSWORD },
				{ TestData.email(B), PASSWORD }, { TestData.email("cccccccccccc"), PASSWORD } };
		for (String[] c : cases) {
			login(c[0], c[1]).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
				.andExpect(jsonPath("$.message").value("계정 정보를 찾을 수 없습니다."));
		}
	}

	@Test
	void loginValidatesInputAndOnlyAcceptsJson() throws Exception {
		mvc.perform(post("/api/portal/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.fields.email").exists())
			.andExpect(jsonPath("$.fields.password").exists());
		// 다른 사이트의 form 전송 모양은 받지 않는다
		mvc.perform(post("/api/portal/auth/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.content("email=a@b.c&password=x"))
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
	}

	@Test
	void logoutClearsCookie() throws Exception {
		String setCookie = mvc.perform(post("/api/portal/auth/logout"))
			.andExpect(status().isNoContent())
			.andReturn()
			.getResponse()
			.getHeader(HttpHeaders.SET_COOKIE);
		assertThat(setCookie).startsWith(JwtTokenService.COOKIE_NAME + "=;").contains("Max-Age=0").contains("Path=/api/portal");
	}

	@Test
	void meRejectsMissingBrokenForeignAndExpiredTokens() throws Exception {
		data.tenantWithPassword(A, "A사", PASSWORD, "ACTIVE", "READY");

		mvc.perform(get("/api/portal/me")).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		mvc.perform(get("/api/portal/me").cookie(new Cookie(JwtTokenService.COOKIE_NAME, "garbage")))
			.andExpect(status().isUnauthorized());

		// 다른 키로 서명한 토큰 (예: Console 운영자 토큰)
		var otherKey = new TetraProperties("test", properties.aws(),
				new TetraProperties.Auth("another-secret-0123456789abcdef0123456789"), properties.passwordLink(),
				properties.apply(), properties.csv(), properties.mail(), properties.monitoring(), properties.publicUrl());
		String foreign = new JwtTokenService(otherKey, Clock.systemUTC()).issue(A);
		mvc.perform(get("/api/portal/me").cookie(new Cookie(JwtTokenService.COOKIE_NAME, foreign)))
			.andExpect(status().isUnauthorized());

		// 9시간 전에 발급된 토큰 (만료 8시간)
		Clock past = Clock.fixed(Instant.now().minus(Duration.ofHours(9)), ZoneOffset.UTC);
		String expired = new JwtTokenService(properties, past).issue(A);
		mvc.perform(get("/api/portal/me").cookie(new Cookie(JwtTokenService.COOKIE_NAME, expired)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void tokenOfDeletedOrNotReadyTenantIsRejected() throws Exception {
		data.tenantWithPassword(A, "A사", PASSWORD, "ACTIVE", "READY");
		Cookie cookie = loginCookie(A);
		mvc.perform(get("/api/portal/me").cookie(cookie)).andExpect(status().isOk());

		data.jdbc().update("UPDATE tetra_platform.tenant SET provisioning_status = 'FAILED' WHERE tenant_id = ?", A);
		mvc.perform(get("/api/portal/me").cookie(cookie)).andExpect(status().isUnauthorized());

		data.deleteTenant(A);
		mvc.perform(get("/api/portal/me").cookie(cookie)).andExpect(status().isUnauthorized());
	}

}
