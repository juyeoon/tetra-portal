package com.tetra.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.tetra.portal.external.DownloadUrlSigner.SignedUrl;
import com.tetra.portal.support.IntegrationTest;

/** UI-TP-6-2 결과 탭 · 발급 이력 CSV (계획서 4-4). 판정 · 숫자는 Console 결과 API와 같아야 한다. */
@ExtendWith(OutputCaptureExtension.class)
class ResultApiTest extends IntegrationTest {

	static final LocalDateTime OPEN = LocalDateTime.of(2026, 9, 25, 10, 0);

	private void readyEvent(String historyPath) {
		data.tenant(A, "A사", "ACTIVE", "READY");
		data.event(1003, A).approved("ENDED", "EXPIRED", "chsk2609").insert(); // stock 10000, plan M(30000명)
		data.summaryAggregated(A, 1003, 1200, 1000, 900, OPEN.plusMinutes(1));
		data.summaryHistory(A, 1003, 10000, 700, 200, "0.0700", historyPath, OPEN.plusHours(3));
		data.issuance(A, A, 1003, "u1", 1, OPEN.plusSeconds(10), "SUCCESS");
		data.issuance(A, A, 1003, "u2", 2, OPEN.plusSeconds(40), "SUCCESS");
		data.issuance(A, A, 1003, "u3", 3, OPEN.plusSeconds(50), "FAILED_SOLDOUT");
		data.issuance(A, A, 1003, "u4", 4, OPEN.plusMinutes(2), "SUCCESS");
	}

	@Test
	void readyResultHasMetricsCompositionAndPerMinute() throws Exception {
		readyEvent("issuance-history/1003.csv");

		mvc.perform(get("/api/portal/events/{id}/result", 1003).cookie(loginCookie(A)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("READY"))
			.andExpect(jsonPath("$.aggregatedAt").value("2026-09-25T13:00:00+09:00"))
			.andExpect(jsonPath("$.result.metrics.successCount").value(700))
			.andExpect(jsonPath("$.result.metrics.totalStock").value(10000))
			.andExpect(jsonPath("$.result.metrics.selloutRate").value(0.07))
			.andExpect(jsonPath("$.result.metrics.peakConcurrent").value(900))
			.andExpect(jsonPath("$.result.metrics.maxCapacity").value(30000))
			.andExpect(jsonPath("$.result.metrics.capacityUsageRate").value(0.03))
			// 이탈 = 세션 1200 − 발급 700 − 품절 200 (번호표 1000이 아니라 세션 기준)
			.andExpect(jsonPath("$.result.composition.abandonedCount").value(300))
			.andExpect(jsonPath("$.result.perMinute.length()").value(2))
			.andExpect(jsonPath("$.result.perMinute[0].minute").value("2026-09-25T10:00:00+09:00"))
			.andExpect(jsonPath("$.result.perMinute[0].successCount").value(2))
			.andExpect(jsonPath("$.result.perMinute[0].soldoutCount").value(1))
			.andExpect(jsonPath("$.result.perMinute[1].minute").value("2026-09-25T10:02:00+09:00"));

		// 상세에도 같은 판정
		mvc.perform(get("/api/portal/events/{id}", 1003).cookie(loginCookie(A)))
			.andExpect(jsonPath("$.status.resultState").value("READY"));
	}

	/**
	 * 이탈은 세션 기준(Console과 같음): 입장했지만 결과(발급 · 품절)를 받지 못한 수.
	 * 세션 11 · 번호표 9 · 발급 5 · 품절 4 → 번호표 기준이면 0이지만 세션 기준 2. 결과보다 세션이 적으면 0.
	 */
	@Test
	void abandonedCountIsSessionsWithoutResult() throws Exception {
		data.tenant(A, "A사", "ACTIVE", "READY");
		data.event(1003, A).approved("ENDED", "EXPIRED", "chsk2609").insert();
		data.summaryAggregated(A, 1003, 11, 9, 11, OPEN.plusMinutes(1));
		data.summaryHistory(A, 1003, 10000, 5, 4, "0.0005", "issuance-history/1003.csv", OPEN.plusHours(3));
		mvc.perform(get("/api/portal/events/{id}/result", 1003).cookie(loginCookie(A)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.composition.successCount").value(5))
			.andExpect(jsonPath("$.result.composition.soldoutCount").value(4))
			.andExpect(jsonPath("$.result.composition.abandonedCount").value(2));

		// 집계 Job을 건너뛰어 세션이 0인데 결과가 있으면 음수 대신 0
		data.jdbc().update("UPDATE t_" + A + ".event_result_summary SET session_count = 0, ticket_count = 0 WHERE event_id = 1003");
		mvc.perform(get("/api/portal/events/{id}/result", 1003).cookie(loginCookie(A)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.composition.abandonedCount").value(0));
	}

	@Test
	void statesBeforeReady() throws Exception {
		data.tenant(A, "A사", "ACTIVE", "READY");
		data.event(1001, A).insert(); // APPLIED — 승인 전
		data.event(1002, A).approved("HOSTING", "OPEN", "abcd2345").insert();
		data.event(1003, A).approved("ENDED", "EXPIRED", "chsk2609").insert();
		data.summaryAggregated(A, 1003, 10, 10, 5, null); // 집계 Job만 — CSV 아직
		data.event(1004, A).approved("END_FAILED", "EXPIRED", "fail2345").insert();
		data.statusLog(A, 1004, "HOSTING", "HOSTING", "END_FAILED", OPEN.plusHours(5), "정리 작업 실패", null);

		mvc.perform(get("/api/portal/events/{id}/result", 1001).cookie(loginCookie(A)))
			.andExpect(jsonPath("$.state").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.endAt").exists())
			.andExpect(jsonPath("$.result").doesNotExist());
		mvc.perform(get("/api/portal/events/{id}/result", 1002).cookie(loginCookie(A)))
			.andExpect(jsonPath("$.state").value("IN_PROGRESS"));
		mvc.perform(get("/api/portal/events/{id}/result", 1003).cookie(loginCookie(A)))
			.andExpect(jsonPath("$.state").value("AGGREGATING"));
		mvc.perform(get("/api/portal/events/{id}/result", 1004).cookie(loginCookie(A)))
			.andExpect(jsonPath("$.state").value("END_FAILED"))
			.andExpect(jsonPath("$.failure.cause").value("정리 작업 실패"));

		// 결과가 없으면 CSV 링크도 없다
		mvc.perform(get("/api/portal/events/{id}/result/history-csv", 1003).cookie(loginCookie(A)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESULT_NOT_READY"));
		verify(urlSigner, never()).presignGet(anyString(), anyString(), any(), anyString());
	}

	@Test
	void csvLinkIsSignedForOwnAuditBucket() throws Exception {
		readyEvent("issuance-history/1003.csv");
		Instant expires = Instant.parse("2026-10-19T03:05:00Z");
		when(urlSigner.presignGet(anyString(), anyString(), any(), anyString()))
			.thenReturn(new SignedUrl("https://signed.example/csv", expires));

		mvc.perform(get("/api/portal/events/{id}/result/history-csv", 1003).cookie(loginCookie(A)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.url").value("https://signed.example/csv"))
			.andExpect(jsonPath("$.expiresAt").value("2026-10-19T12:05:00+09:00"));
		verify(urlSigner).presignGet(eq("tetra-audit-logs-test-" + A), eq("issuance-history/1003.csv"),
				eq(Duration.ofMinutes(5)), eq("issuance-history-1003.csv"));
	}

	@Test
	void fullS3PathIsAcceptedOnlyForOwnBucket(CapturedOutput output) throws Exception {
		readyEvent("s3://tetra-audit-logs-test-" + A + "/issuance-history/1003.csv");
		when(urlSigner.presignGet(anyString(), anyString(), any(), anyString()))
			.thenReturn(new SignedUrl("https://signed.example/csv", Instant.now().plusSeconds(300)));
		mvc.perform(get("/api/portal/events/{id}/result/history-csv", 1003).cookie(loginCookie(A)))
			.andExpect(status().isOk());
		verify(urlSigner).presignGet(eq("tetra-audit-logs-test-" + A), eq("issuance-history/1003.csv"), any(),
				anyString());

		// 다른 테넌트 버킷을 가리키면 링크를 만들지 않는다
		data.jdbc().update("UPDATE t_" + A + ".event_result_summary SET history_file_path = ? WHERE event_id = 1003",
				"s3://tetra-audit-logs-test-" + B + "/issuance-history/2001.csv");
		mvc.perform(get("/api/portal/events/{id}/result/history-csv", 1003).cookie(loginCookie(A)))
			.andExpect(status().isInternalServerError());
		assertThat(output.getAll()).contains("다른 버킷을 가리킵니다");
	}

	@Test
	void otherTenantsResultIsNotFoundAndForeignRowsAreRejected(CapturedOutput output) throws Exception {
		readyEvent("issuance-history/1003.csv");
		data.tenant(B, "B사", "ACTIVE", "READY");
		mvc.perform(get("/api/portal/events/{id}/result", 1003).cookie(loginCookie(B)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
		mvc.perform(get("/api/portal/events/{id}/result/history-csv", 1003).cookie(loginCookie(B)))
			.andExpect(status().isNotFound());

		// A 스키마의 발급 이력에 다른 tenant_id 행이 섞여 있으면 집계하지 않는다
		data.issuance(A, B, 1003, "intruder", 99, OPEN.plusSeconds(20), "SUCCESS");
		mvc.perform(get("/api/portal/events/{id}/result", 1003).cookie(loginCookie(A)))
			.andExpect(status().isInternalServerError());
		assertThat(output.getAll()).contains("테넌트 불일치");
	}

}
