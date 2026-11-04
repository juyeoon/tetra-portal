package com.tetra.portal.event.apply;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tetra.portal.auth.CurrentTenant;
import com.tetra.portal.common.audit.AuditContext;
import com.tetra.portal.common.error.ApiErrorCodes;
import com.tetra.portal.common.error.ErrorCode;
import com.tetra.portal.domain.ApplicationStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** UI-TP-07~10 이벤트 신청 (WF1-1). */
@Tag(name = "이벤트 신청", description = "UI-TP-07~10 — WF1-1")
@RestController
@RequestMapping("/api/portal/events")
public class ApplyController {

	private final ApplyService service;

	public ApplyController(ApplyService service) {
		this.service = service;
	}

	public record ApplyResponse(Long eventId, ApplicationStatus applicationStatus) {
	}

	@Operation(summary = "이벤트 신청",
			description = """
					multipart/form-data — `application` 파트(JSON, Content-Type: application/json) + `banner` 파트(PNG · JPEG, 2MB 이하).
					검사를 통과하면 202 { eventId, applicationStatus: APPLYING }. 배너는 그 뒤에 올라가고, 결과는 상세의 신청 상태
					(APPLIED / APPLY_FAILED — 원인은 진행 기록 detail)로 확인한다. 실패한 신청은 고치지 않고 새로 신청한다.""")
	@ApiErrorCodes({ ErrorCode.INVALID_INPUT, ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.TENANT_NOT_READY })
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ApplyResponse> apply(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
			@Valid @RequestPart("application") ApplyRequest application,
			@RequestPart(name = "banner", required = false) MultipartFile banner) {
		Long eventId = service.apply(CurrentTenant.id(jwt), application, banner);
		AuditContext.current().event(eventId);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ApplyResponse(eventId, ApplicationStatus.APPLYING));
	}

}
