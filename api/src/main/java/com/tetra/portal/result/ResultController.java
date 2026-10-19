package com.tetra.portal.result;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tetra.portal.auth.CurrentTenant;
import com.tetra.portal.common.error.ApiErrorCodes;
import com.tetra.portal.common.error.ErrorCode;
import com.tetra.portal.tenancy.TenantRouter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/** UI-TP-06 결과 탭 — 지표 · 구성 · 분 단위 추이 · 발급 이력 CSV. 로그인한 테넌트의 이벤트만. */
@Tag(name = "이벤트 결과", description = "UI-TP-6-2 결과 탭")
@RestController
@RequestMapping("/api/portal/events/{eventId}/result")
public class ResultController {

	private final TenantRouter router;

	private final ResultReader reader;

	private final HistoryCsvService csv;

	public ResultController(TenantRouter router, ResultReader reader, HistoryCsvService csv) {
		this.router = router;
		this.reader = reader;
		this.csv = csv;
	}

	@Operation(summary = "결과", description = "state: READY(결과 있음) · AGGREGATING(집계 중) · IN_PROGRESS(진행 전 · 중) · END_FAILED")
	@ApiErrorCodes({ ErrorCode.INVALID_INPUT, ErrorCode.EVENT_NOT_FOUND, ErrorCode.TENANT_NOT_READY })
	@GetMapping
	public ResultResponse result(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
			@PathVariable Long eventId) {
		return router.inTenant(CurrentTenant.id(jwt), () -> reader.result(eventId));
	}

	@Operation(summary = "발급 이력 CSV 다운로드 링크",
			description = "요청할 때마다 새 링크(기본 5분 뒤 만료, expiresAt). 링크를 열면 issuance-history-{eventId}.csv로 저장된다. 결과가 READY일 때만 — 아니면 409")
	@ApiErrorCodes({ ErrorCode.INVALID_INPUT, ErrorCode.EVENT_NOT_FOUND, ErrorCode.RESULT_NOT_READY,
			ErrorCode.TENANT_NOT_READY })
	@GetMapping("/history-csv")
	public ResultResponse.CsvDownload historyCsv(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
			@PathVariable Long eventId) {
		return csv.link(CurrentTenant.id(jwt), eventId);
	}

}
