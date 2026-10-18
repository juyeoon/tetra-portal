package com.tetra.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import com.tetra.portal.support.IntegrationTest;
import com.tetra.portal.support.TestData;

/** UI-TP-04 비밀번호 설정 — Console이 만든 링크(?t=&exp=&sig=)를 받는다 (계획서 4-1 · 5-1). */
class PasswordSetupApiTest extends IntegrationTest {

	static final String HMAC_SECRET = "test-hmac-secret"; // application-test.yml과 같은 값

	static final String NEW_PASSWORD = "new-password-1";

	/**
	 * Console의 PasswordLinkSigner.sign과 같은 계산을 시험 안에 따로 둔다(앱 코드를 쓰지 않음) —
	 * 두 서비스의 서명 규칙이 어긋나면 이 시험이 깨진다.
	 */
	static String consoleSign(String tenantId, long exp, String currentPassword) throws Exception {
		String message = tenantId + exp + (currentPassword == null ? "" : currentPassword);
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(HMAC_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
	}

	record Link(String t, long exp, String sig) {
	}

	/** Console이 지금 보낼 링크 (24시간). */
	private Link consoleLink(String tenantId) throws Exception {
		long exp = Instant.now().plus(Duration.ofHours(24)).getEpochSecond();
		return new Link(tenantId, exp, consoleSign(tenantId, exp, data.passwordHash(tenantId)));
	}

	private ResultActions verify(Link link) throws Exception {
		return mvc.perform(post("/api/portal/password/setup/verify").contentType(MediaType.APPLICATION_JSON)
			.content("{\"t\":\"%s\",\"exp\":%d,\"sig\":\"%s\"}".formatted(link.t(), link.exp(), link.sig())));
	}

	private ResultActions setup(Link link, String password) throws Exception {
		return mvc.perform(post("/api/portal/password/setup").contentType(MediaType.APPLICATION_JSON)
			.content("{\"t\":\"%s\",\"exp\":%d,\"sig\":\"%s\",\"password\":\"%s\"}".formatted(link.t(), link.exp(),
					link.sig(), password)));
	}

	private ResultActions login(String tenantId, String password) throws Exception {
		return mvc.perform(post("/api/portal/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(TestData.email(tenantId), password)));
	}

	@Test
	void consoleLinkSetsPasswordThenLoginWorks() throws Exception {
		data.tenant(A, "A사", "INACTIVE", "READY");
		Link link = consoleLink(A);

		verify(link).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(TestData.email(A)));
		setup(link, NEW_PASSWORD).andExpect(status().isNoContent());

		assertThat(new BCryptPasswordEncoder().matches(NEW_PASSWORD, data.passwordHash(A))).isTrue();
		login(A, NEW_PASSWORD).andExpect(status().isOk());
	}

	@Test
	void linkWorksOnlyOnce() throws Exception {
		data.tenant(A, "A사", "INACTIVE", "READY");
		Link link = consoleLink(A);
		setup(link, NEW_PASSWORD).andExpect(status().isNoContent());
		String firstHash = data.passwordHash(A);

		// 비밀번호 해시가 서명에 들어 있어 같은 링크는 더 이상 맞지 않는다
		verify(link).andExpect(status().isGone()).andExpect(jsonPath("$.code").value("PASSWORD_LINK_INVALID"));
		setup(link, "another-password").andExpect(status().isGone());
		assertThat(data.passwordHash(A)).isEqualTo(firstHash);
	}

	@Test
	void expiredTamperedOrForeignLinksAreAllGone() throws Exception {
		data.tenant(A, "A사", "INACTIVE", "READY");
		data.tenant(B, "B사", "INACTIVE", "READY");
		Link good = consoleLink(A);
		long past = Instant.now().minusSeconds(60).getEpochSecond();

		Link[] bad = { new Link(A, past, consoleSign(A, past, null)), // 만료 (서명은 맞음)
				new Link(A, good.exp(), good.sig().substring(1) + "A"), // 서명 변조
				new Link(A, good.exp() + 3600, good.sig()), // 만료 시각 연장 시도
				new Link(B, good.exp(), good.sig()), // 다른 테넌트에 A의 서명
				new Link("cccccccccccc", good.exp(), consoleSign("cccccccccccc", good.exp(), null)), // 없는 테넌트
				new Link("NOT-A-TENANT", good.exp(), good.sig()) }; // 형식 오류
		for (Link link : bad) {
			verify(link).andExpect(status().isGone()).andExpect(jsonPath("$.message").value("링크가 만료되었거나 이미 사용되었습니다."));
			setup(link, NEW_PASSWORD).andExpect(status().isGone());
		}
		assertThat(data.passwordHash(A)).isNull();
		assertThat(data.passwordHash(B)).isNull();
	}

	@Test
	void passwordLengthIsChecked() throws Exception {
		data.tenant(A, "A사", "INACTIVE", "READY");
		setup(consoleLink(A), "short").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.fields.password").exists());
		assertThat(data.passwordHash(A)).isNull();
	}

	@Test
	void provisioningTenantCanSetPasswordButLogsInOnlyWhenReady() throws Exception {
		data.tenant(A, "A사", "INACTIVE", "PROVISIONING");
		setup(consoleLink(A), NEW_PASSWORD).andExpect(status().isNoContent());
		login(A, NEW_PASSWORD).andExpect(status().isUnauthorized());

		data.jdbc().update("UPDATE tetra_platform.tenant SET provisioning_status = 'READY' WHERE tenant_id = ?", A);
		login(A, NEW_PASSWORD).andExpect(status().isOk());
	}

}
