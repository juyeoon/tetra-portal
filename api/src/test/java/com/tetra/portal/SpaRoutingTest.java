package com.tetra.portal;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.tetra.portal.support.IntegrationTest;

/**
 * 화면과 API를 한 이미지로 서빙. 화면 파일은 인증 없이, /api/**만 인증.
 * 테스트용 화면 파일은 src/test/resources/static/에 있다(실제로는 web/dist가 들어간다).
 */
class SpaRoutingTest extends IntegrationTest {

	static final String INDEX_MARK = "tetra-portal-test-index";

	@Test
	void indexAndAssetsAreServedWithoutLogin() throws Exception {
		// "/"는 Spring Boot 환영 페이지가 index.html로 forward한다(실제 서버는 forward를 따라감, MockMvc는 대상만 기록)
		mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
		mvc.perform(get("/index.html")).andExpect(status().isOk()).andExpect(content().string(containsString(INDEX_MARK)));
		mvc.perform(get("/assets/app.js"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("tetra-portal-test-asset")));
	}

	@Test
	void spaRoutesFallBackToIndex() throws Exception {
		// 화면 라우터 주소 (web/src 라우트)
		for (String path : new String[] { "/login", "/events", "/events/1043", "/events/new", "/password/setup",
				"/notifications", "/reports", "/settings" }) {
			mvc.perform(get(path)).andExpect(status().isOk()).andExpect(content().string(containsString(INDEX_MARK)));
		}
	}

	@Test
	void missingFilesWithExtensionAre404NotIndex() throws Exception {
		mvc.perform(get("/assets/missing.js"))
			.andExpect(status().isNotFound())
			.andExpect(content().string(not(containsString(INDEX_MARK))));
		mvc.perform(get("/favicon.ico")).andExpect(status().isNotFound());
	}

	@Test
	void apiRequiresLoginAndNeverFallsBackToIndex() throws Exception {
		mvc.perform(get("/api/portal/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		mvc.perform(get("/api/portal/unknown")).andExpect(status().isUnauthorized());
		mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
	}

	@Test
	void healthCheckIsOpenForAlb() throws Exception {
		// ALB 대상 그룹 헬스 체크 경로 (인프라 약속 3절) — DB까지 확인한다
		mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
		mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
	}

}
