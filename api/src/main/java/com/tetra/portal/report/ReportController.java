package com.tetra.portal.report;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tetra.portal.auth.CurrentTenant;
import com.tetra.portal.common.error.ApiErrorCodes;
import com.tetra.portal.common.error.ErrorCode;
import com.tetra.portal.external.AuditFileLinks;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/** UI-TP-12 리포트. */
@Tag(name = "리포트", description = "UI-TP-12")
@RestController
@RequestMapping("/api/portal/reports")
public class ReportController {

	private final ReportService service;

	public ReportController(ReportService service) {
		this.service = service;
	}

	@Operation(summary = "리포트 목록", description = "최근 생성 먼저")
	@ApiErrorCodes({ ErrorCode.TENANT_NOT_READY })
	@GetMapping
	public List<ReportService.ReportItem> list(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
		return service.list(CurrentTenant.id(jwt));
	}

	@Operation(summary = "다운로드 링크", description = "presigned URL(기본 5분) — 열면 report-{id}.{확장자}로 저장")
	@ApiErrorCodes({ ErrorCode.INVALID_INPUT, ErrorCode.REPORT_NOT_FOUND, ErrorCode.TENANT_NOT_READY })
	@GetMapping("/{reportId}/download")
	public AuditFileLinks.Link download(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
			@PathVariable Long reportId) {
		return service.download(CurrentTenant.id(jwt), reportId);
	}

	@Operation(summary = "메일로 보내기",
			description = "로그인 계정 이메일로 안내 메일(지금은 첨부 없이 Portal 링크만). SES가 받지 않으면 503")
	@ApiErrorCodes({ ErrorCode.INVALID_INPUT, ErrorCode.REPORT_NOT_FOUND, ErrorCode.TENANT_NOT_READY,
			ErrorCode.MAIL_SEND_FAILED })
	@PostMapping("/{reportId}/email")
	public ResponseEntity<Void> email(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
			@PathVariable Long reportId) {
		service.email(CurrentTenant.id(jwt), reportId);
		return ResponseEntity.noContent().build();
	}

}
