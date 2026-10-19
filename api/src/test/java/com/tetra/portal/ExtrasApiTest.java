package com.tetra.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;

import com.tetra.portal.external.DownloadUrlSigner.SignedUrl;
import com.tetra.portal.external.MailGateway;
import com.tetra.portal.support.IntegrationTest;
import com.tetra.portal.support.TestData;

/** P5 — 알림(UI-TP-13) · 리포트(UI-TP-12) · 설정(UI-TP-14) · 문의 메일(UI-TP-03). */
class ExtrasApiTest extends IntegrationTest {

	// ---------------------------------------------------------------- 알림

	@Test
	void notificationsAreListedNewestFirstAndMarkedRead() throws Exception {
		data.tenant(A, "A사", "ACTIVE", "READY");
		data.event(1001, A).insert();
		long older = data.notification(A, 1001L, "INFO", TestData.BASE, "신청이 승인되었습니다.", false);
		long newer = data.notification(A, null, "URGENT", TestData.BASE.plusHours(1), "점검 안내", false);

		mvc.perform(get("/api/portal/notifications").cookie(loginCookie(A)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].notificationId").value(newer))
			.andExpect(jsonPath("$[0].priority").value("URGENT"))
			.andExpect(jsonPath("$[0].eventId").doesNotExist())
			.andExpect(jsonPath("$[1].eventId").value(1001))
			.andExpect(jsonPath("$[1].read").value(false));

		mvc.perform(patch("/api/portal/notifications/{id}/read", older).cookie(loginCookie(A)))
			.andExpect(status().isNoContent());
		mvc.perform(patch("/api/portal/notifications/{id}/read", older).cookie(loginCookie(A)))
			.andExpect(status().isNoContent()); // 멱등
		assertThat(data.count("SELECT COUNT(*) FROM t_" + A + ".tenant_notification WHERE is_read = 1")).isEqualTo(1);
	}

	@Test
	void otherTenantsNotificationIsNotFound() throws Exception {
		data.tenant(A, "A사", "ACTIVE", "READY");
		data.tenant(B, "B사", "ACTIVE", "READY");
		long bNotification = data.notification(B, null, "INFO", TestData.BASE, "B 전용", false);

		mvc.perform(get("/api/portal/notifications").cookie(loginCookie(A))).andExpect(jsonPath("$.length()").value(0));
		mvc.perform(patch("/api/portal/notifications/{id}/read", bNotification).cookie(loginCookie(A)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
		assertThat(data.count("SELECT COUNT(*) FROM t_" + B + ".tenant_notification WHERE is_read = 1")).isZero();
	}

	// ---------------------------------------------------------------- 리포트

	@Test
	void reportsListDownloadAndEmail() throws Exception {
		data.tenant(A, "A사", "ACTIVE", "READY");
		long sept = data.report(A, "9월 리포트", "reports/2026-09.pdf", TestData.BASE);
		long oct = data.report(A, "10월 리포트", "reports/2026-10.pdf", TestData.BASE.plusDays(30));

		mvc.perform(get("/api/portal/reports").cookie(loginCookie(A)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].reportId").value(oct))
			.andExpect(jsonPath("$[1].name").value("9월 리포트"))
			.andExpect(jsonPath("$[1].periodStart").value("2026-10-01T10:00:00+09:00"));

		when(urlSigner.presignGet(anyString(), anyString(), any(), anyString()))
			.thenReturn(new SignedUrl("https://signed.example/report", Instant.parse("2026-10-19T03:05:00Z")));
		mvc.perform(get("/api/portal/reports/{id}/download", sept).cookie(loginCookie(A)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.url").value("https://signed.example/report"))
			.andExpect(jsonPath("$.expiresAt").value("2026-10-19T12:05:00+09:00"));
		verify(urlSigner).presignGet(eq("tetra-audit-logs-test-" + A), eq("reports/2026-09.pdf"), any(),
				eq("report-" + sept + ".pdf"));

		mvc.perform(post("/api/portal/reports/{id}/email", sept).cookie(loginCookie(A)))
			.andExpect(status().isNoContent());
		ArgumentCaptor<MailGateway.Mail> sent = ArgumentCaptor.forClass(MailGateway.Mail.class);
		verify(mail).send(sent.capture());
		assertThat(sent.getValue().to()).isEqualTo(TestData.email(A));
		assertThat(sent.getValue().subject()).contains("9월 리포트");
		assertThat(sent.getValue().textBody()).contains("/reports").doesNotContain("signed.example");
	}

	@Test
	void reportMailFailureAndOtherTenantReport() throws Exception {
		data.tenant(A, "A사", "ACTIVE", "READY");
		data.tenant(B, "B사", "ACTIVE", "READY");
		long report = data.report(A, "9월 리포트", "reports/2026-09.pdf", TestData.BASE);
		// report_id는 테넌트 스키마마다 따로 증가한다 — A에 없는 번호가 되도록 B에 여러 개
		data.report(B, "B 리포트 1", "reports/b1.pdf", TestData.BASE);
		data.report(B, "B 리포트 2", "reports/b2.pdf", TestData.BASE);
		long bReport = data.report(B, "B 리포트 3", "reports/b3.pdf", TestData.BASE);
		assertThat(bReport).isNotEqualTo(report);

		when(mail.send(any())).thenReturn(false);
		mvc.perform(post("/api/portal/reports/{id}/email", report).cookie(loginCookie(A)))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("MAIL_SEND_FAILED"));

		mvc.perform(get("/api/portal/reports/{id}/download", bReport).cookie(loginCookie(A)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
		verify(urlSigner, never()).presignGet(anyString(), anyString(), any(), anyString());
	}

	// ---------------------------------------------------------------- 설정

	@Test
	void emailSettingCanBeToggled() throws Exception {
		data.tenant(A, "A사", "ACTIVE", "READY");
		mvc.perform(get("/api/portal/settings").cookie(loginCookie(A)))
			.andExpect(jsonPath("$.emailEnabled").value(false))
			.andExpect(jsonPath("$.slackAvailable").value(false));

		mvc.perform(patch("/api/portal/settings/email").cookie(loginCookie(A)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"enabled\":true}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.emailEnabled").value(true));
		assertThat(data.count("SELECT COUNT(*) FROM tetra_platform.tenant WHERE tenant_id = ? AND email_notification_enabled = 1", A))
			.isEqualTo(1);

		mvc.perform(patch("/api/portal/settings/email").cookie(loginCookie(A)).contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.enabled").exists());
		mvc.perform(get("/api/portal/settings")).andExpect(status().isUnauthorized());
	}

	// ---------------------------------------------------------------- 문의 메일

	@Test
	void supportMailGoesOnlyToSupportAddressWithReplyTo() throws Exception {
		mvc.perform(post("/api/portal/support-mail").contentType(MediaType.APPLICATION_JSON)
			.content("{\"from\":\"kim@shop.example.com\",\"subject\":\"비밀번호 문의\\r\\nBcc: evil@x.com\",\"body\":\"링크가 만료됐어요\"}"))
			.andExpect(status().isNoContent());

		ArgumentCaptor<MailGateway.Mail> sent = ArgumentCaptor.forClass(MailGateway.Mail.class);
		verify(mail).send(sent.capture());
		assertThat(sent.getValue().to()).isEqualTo("tetra.ops.1@gmail.com");
		assertThat(sent.getValue().replyTo()).isEqualTo("kim@shop.example.com");
		assertThat(sent.getValue().subject()).isEqualTo("[Portal 문의] 비밀번호 문의 Bcc: evil@x.com")
			.doesNotContain("\n");
		assertThat(sent.getValue().textBody()).contains("kim@shop.example.com").contains("링크가 만료됐어요");
	}

	@Test
	void supportMailIsValidatedAndReportsSendFailure() throws Exception {
		mvc.perform(post("/api/portal/support-mail").contentType(MediaType.APPLICATION_JSON)
			.content("{\"from\":\"not-an-email\",\"subject\":\"\",\"body\":\"x\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.from").value("이메일 형식이 아닙니다."))
			.andExpect(jsonPath("$.fields.subject").exists());
		verify(mail, never()).send(any());

		when(mail.send(any())).thenReturn(false);
		mvc.perform(post("/api/portal/support-mail").contentType(MediaType.APPLICATION_JSON)
			.content("{\"from\":\"kim@shop.example.com\",\"subject\":\"문의\",\"body\":\"x\"}"))
			.andExpect(status().isServiceUnavailable());
	}

}
