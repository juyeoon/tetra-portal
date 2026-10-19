package com.tetra.portal.settings;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tetra.portal.auth.CurrentTenant;
import com.tetra.portal.common.error.ApiErrorCodes;
import com.tetra.portal.common.error.ErrorCode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** UI-TP-14 설정 — 이메일 알림. Slack은 MVP 1차 제외(계획서 4-7). */
@Tag(name = "설정", description = "UI-TP-14")
@RestController
@RequestMapping("/api/portal/settings")
public class SettingsController {

	private final SettingsService service;

	public SettingsController(SettingsService service) {
		this.service = service;
	}

	public record EmailSettingRequest(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull(message = "필수 입력입니다.") Boolean enabled) {
	}

	@Operation(summary = "설정 조회", description = "slackAvailable = false — 화면은 Slack 영역을 '준비 중'으로")
	@GetMapping
	public SettingsService.SettingsResponse get(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
		return service.get(CurrentTenant.id(jwt));
	}

	@Operation(summary = "이메일 알림 켜기 · 끄기", description = "바뀐 설정을 돌려준다")
	@ApiErrorCodes({ ErrorCode.INVALID_INPUT })
	@PatchMapping(path = "/email", consumes = MediaType.APPLICATION_JSON_VALUE)
	public SettingsService.SettingsResponse email(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody EmailSettingRequest request) {
		return service.setEmail(CurrentTenant.id(jwt), request.enabled());
	}

}
