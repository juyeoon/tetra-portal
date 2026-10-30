package com.tetra.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.tetra.portal.support.IntegrationTest;
import com.tetra.portal.support.TestData;

/** UI-TP-05 목록 · UI-TP-06 상세 · 요금제 · 테넌트 격리 (계획서 4-2). */
@ExtendWith(OutputCaptureExtension.class)
class EventQueryApiTest extends IntegrationTest {

	@Test
	void plansShowOnlyVisibleSmallestFirst() throws Exception {
		data.tenant(A, "A사", "ACTIVE", "READY");
		mvc.perform(get("/api/portal/plans").cookie(loginCookie(A)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(3)) // HIDDEN(시험 시드) · XL(V3)은 is_visible = 0
			.andExpect(jsonPath("$[0].planCode").value("S"))
			.andExpect(jsonPath("$[0].maxCapacity").value(10000))
			.andExpect(jsonPath("$[2].planCode").value("L"))
			// 금액은 내보내지 않는다 (2026-10-19 결정)
			.andExpect(jsonPath("$[0].hourlyPrice").doesNotExist())
			.andExpect(jsonPath("$[0].hostingHourlyPrice").doesNotExist());
		mvc.perform(get("/api/portal/plans")).andExpect(status().isUnauthorized());
	}

	@Test
	void listShowsOnlyOwnEventsNewestFirst() throws Exception {
		data.tenant(A, "A사", "ACTIVE", "READY");
		data.tenant(B, "B사", "ACTIVE", "READY");
		data.event(1001, A).name("A 첫 신청").insert();
		data.event(1002, A).name("A 승인됨").appliedAt(TestData.BASE.plusDays(1)).approved("PENDING", "SCHEDULED", "abcd2345").insert();
		data.billing(A, 1002, "25000.00", "2000.00", "189000.00");
		data.event(2001, B).name("B 이벤트").insert();

		mvc.perform(get("/api/portal/events").cookie(loginCookie(A)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].eventId").value(1002))
			.andExpect(jsonPath("$[0].applicationStatus").value("APPROVED"))
			.andExpect(jsonPath("$[0].hostingStatus").value("PENDING"))
			.andExpect(jsonPath("$[0].eventStatus").value("SCHEDULED"))
			.andExpect(jsonPath("$[0].estimatedCost").doesNotExist())
			.andExpect(jsonPath("$[0].appliedAt").value("2026-10-02T10:00:00+09:00"))
			.andExpect(jsonPath("$[1].eventId").value(1001))
			.andExpect(jsonPath("$[1].hostingStatus").value(nullValue()));

		mvc.perform(get("/api/portal/events").cookie(loginCookie(B)))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].name").value("B 이벤트"));
	}

	@Test
	void detailShowsApplicationCouponsLayoutBillingDomainAndLogs() throws Exception {
		data.tenant(A, "A사", "ACTIVE", "READY");
		data.event(1002, A).name("가을 쿠폰").approved("HOSTING", "OPEN", "abcd2345").insert();
		data.coupon(A, 1002, "10% 할인", "전 상품 10%");
		data.coupon(A, 1002, "무료 배송", "3만원 이상");
		data.billing(A, 1002, "25000.00", "2000.00", "189000.00");
		data.statusLog(A, 1002, "APPLICATION", null, "APPLYING", TestData.BASE, null, null);
		data.statusLog(A, 1002, "APPLICATION", "APPLYING", "APPLIED", TestData.BASE.plusMinutes(1), null, null);
		data.statusLog(A, 1002, "APPLICATION", "APPLIED", "APPROVED", TestData.BASE.plusHours(1), null, 1L);
		data.statusLog(A, 1002, "HOSTING", "PENDING", "HOSTING", TestData.BASE.plusHours(2), null, null);

		mvc.perform(get("/api/portal/events/{id}", 1002).cookie(loginCookie(A)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.eventId").value(1002))
			.andExpect(jsonPath("$.name").value("가을 쿠폰"))
			.andExpect(jsonPath("$.eventType").value("COUPON"))
			.andExpect(jsonPath("$.stockCount").value(10000))
			.andExpect(jsonPath("$.endpointUrl").value("https://shop.example.com/event"))
			.andExpect(jsonPath("$.applicantName").value("A사 담당자"))
			.andExpect(jsonPath("$.status.applicationStatus").value("APPROVED"))
			.andExpect(jsonPath("$.status.rejectReason").value(nullValue()))
			.andExpect(jsonPath("$.coupons.length()").value(2))
			.andExpect(jsonPath("$.coupons[0].name").value("10% 할인"))
			.andExpect(jsonPath("$.coupons[1].name").value("무료 배송"))
			.andExpect(jsonPath("$.layout.couponList.v").value("MIDDLE"))
			.andExpect(jsonPath("$.layout.theme").value("WHITE"))
			.andExpect(jsonPath("$.billing").doesNotExist())
			// 테넌트 root_domain(고객사 도메인 A.example.com)이 아니라 플랫폼 도메인(EVENT_ROOT_DOMAIN 기본값)
			.andExpect(jsonPath("$.domain").value("abcd2345." + A + ".tetraops.cloud"))
			.andExpect(jsonPath("$.logs.length()").value(4))
			.andExpect(jsonPath("$.logs[0].actor").value("TENANT"))
			.andExpect(jsonPath("$.logs[1].actor").value("SYSTEM"))
			.andExpect(jsonPath("$.logs[2].actor").value("OPERATOR"))
			.andExpect(jsonPath("$.logs[3].statusType").value("HOSTING"))
			.andExpect(jsonPath("$.logs[3].actor").value("SYSTEM"));
	}

	@Test
	void domainIsHiddenBeforeApprovalAndAfterExpiry() throws Exception {
		data.tenant(A, "A사", "ACTIVE", "READY");
		data.event(1001, A).insert(); // APPLIED
		data.event(1002, A).approved("ENDED", "EXPIRED", "abcd2345").insert();
		data.event(1003, A).rejected("기간이 너무 짧습니다").insert();

		mvc.perform(get("/api/portal/events/{id}", 1001).cookie(loginCookie(A)))
			.andExpect(jsonPath("$.domain").value(nullValue()));
		mvc.perform(get("/api/portal/events/{id}", 1002).cookie(loginCookie(A)))
			.andExpect(jsonPath("$.domain").value(nullValue()));
		mvc.perform(get("/api/portal/events/{id}", 1003).cookie(loginCookie(A)))
			.andExpect(jsonPath("$.status.applicationStatus").value("REJECTED"))
			.andExpect(jsonPath("$.status.rejectReason").value("기간이 너무 짧습니다"))
			.andExpect(jsonPath("$.domain").value(nullValue()));
	}

	@Test
	void logDetailIsSentOnlyForApplicationLogs() throws Exception {
		data.tenant(A, "A사", "ACTIVE", "READY");
		// 신청 실패 원인 · 반려 사유는 고객이 볼 내용 — 그대로
		data.event(1001, A).status("APPLY_FAILED").insert();
		data.statusLog(A, 1001, "APPLICATION", "APPLYING", "APPLY_FAILED", TestData.BASE, "배너 업로드 실패", null);
		data.event(1002, A).rejected("기간이 너무 짧습니다").insert();
		data.statusLog(A, 1002, "APPLICATION", "APPLIED", "REJECTED", TestData.BASE, "기간이 너무 짧습니다", 1L);
		// 호스팅 · 이벤트 실패 원인은 워크플로 실행 오류 원문 — 내리지 않는다(상태 값은 그대로)
		data.event(1003, A).approved("DEPLOY_FAILED", "SCHEDULED", "fail2345").insert();
		data.statusLog(A, 1003, "HOSTING", "PENDING", "DEPLOY_FAILED", TestData.BASE, "FAILED: States.TaskFailed / gitops", null);
		data.statusLog(A, 1003, "EVENT", "SCHEDULED", "OPEN_FAILED", TestData.BASE.plusHours(1), "TIMED_OUT", null);

		mvc.perform(get("/api/portal/events/{id}", 1001).cookie(loginCookie(A)))
			.andExpect(jsonPath("$.logs[0].detail").value("배너 업로드 실패"));
		mvc.perform(get("/api/portal/events/{id}", 1002).cookie(loginCookie(A)))
			.andExpect(jsonPath("$.logs[0].detail").value("기간이 너무 짧습니다"));
		String body = mvc.perform(get("/api/portal/events/{id}", 1003).cookie(loginCookie(A)))
			.andExpect(jsonPath("$.status.hostingStatus").value("DEPLOY_FAILED"))
			.andExpect(jsonPath("$.logs[0].newStatus").value("DEPLOY_FAILED"))
			.andExpect(jsonPath("$.logs[0].detail").value(nullValue()))
			.andExpect(jsonPath("$.logs[1].newStatus").value("OPEN_FAILED"))
			.andExpect(jsonPath("$.logs[1].detail").value(nullValue()))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(body).doesNotContain("States.TaskFailed").doesNotContain("TIMED_OUT");
	}

	@Test
	void otherTenantsEventIsNotFound() throws Exception {
		data.tenant(A, "A사", "ACTIVE", "READY");
		data.tenant(B, "B사", "ACTIVE", "READY");
		data.event(2001, B).name("B의 비밀 이벤트").insert();

		// A로 로그인해 B의 eventId를 넣어도 A 스키마에는 없다
		String body = mvc.perform(get("/api/portal/events/{id}", 2001).cookie(loginCookie(A)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(body).doesNotContain("비밀");
		mvc.perform(get("/api/portal/events/{id}", 9999).cookie(loginCookie(A))).andExpect(status().isNotFound());
		mvc.perform(get("/api/portal/events/{id}", "abc").cookie(loginCookie(A)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	void rowWithAnotherTenantIdIsRejectedByDoubleCheck(CapturedOutput output) throws Exception {
		data.tenant(A, "A사", "ACTIVE", "READY");
		data.tenant(B, "B사", "ACTIVE", "READY");
		// A 스키마에 tenant_id가 B인 행이 잘못 들어가 있다
		data.event(1001, A).name("섞인 행").rowTenantId(B).insert();

		String body = mvc.perform(get("/api/portal/events/{id}", 1001).cookie(loginCookie(A)))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(body).doesNotContain("섞인 행");
		mvc.perform(get("/api/portal/events").cookie(loginCookie(A))).andExpect(status().isInternalServerError());
		assertThat(output.getAll()).contains("테넌트 불일치");
	}

}
