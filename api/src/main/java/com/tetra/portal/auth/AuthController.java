package com.tetra.portal.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tetra.portal.common.error.ApiErrorCodes;
import com.tetra.portal.common.error.ErrorCode;
import com.tetra.portal.domain.platform.Tenant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** UI-TP-01/02 로그인 · 로그아웃 · 로그인한 계정(헤더 · 설정 화면). */
@Tag(name = "인증", description = "UI-TP-01/02 로그인 · 로그아웃 · 계정 정보")
@RestController
@RequestMapping("/api/portal")
public class AuthController {

	private final AuthService authService;

	private final JwtTokenService tokens;

	public AuthController(AuthService authService, JwtTokenService tokens) {
		this.authService = authService;
		this.tokens = tokens;
	}

	public record LoginRequest(
			@Schema(example = "admin@tenant-company.com", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank(message = "필수 입력입니다.") String email,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank(message = "필수 입력입니다.") String password) {

		@Override
		public String toString() {
			return "LoginRequest[email=" + email + ", password=****]";
		}

	}

	/** 화면의 Account 타입과 같은 이름 (web/src/types). */
	public record AccountResponse(@Schema(description = "테넌트 ID (12자)") String tenantId,
			@Schema(description = "고객사 이름") String companyName,
			@Schema(description = "담당자 이름") String managerName,
			@Schema(description = "로그인 이메일") String email) {

		static AccountResponse of(Tenant tenant) {
			return new AccountResponse(tenant.getTenantId(), tenant.getName(), tenant.getContactName(), tenant.getEmail());
		}

	}

	@Operation(summary = "UI-TP-01 로그인",
			description = "성공하면 HttpOnly · Secure 쿠키 TETRA_PORTAL_TOKEN(8시간, Path=/api/portal)을 내려준다. "
					+ "이메일이 없든 비밀번호가 틀리든 비밀번호 설정 전이든 같은 401 LOGIN_FAILED")
	@SecurityRequirements
	@ApiErrorCodes({ ErrorCode.INVALID_INPUT, ErrorCode.LOGIN_FAILED })
	@PostMapping(path = "/auth/login", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<AccountResponse> login(@Valid @RequestBody LoginRequest request) {
		AuthService.LoginResult result = authService.login(request.email(), request.password());
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, tokens.loginCookie(result.token()).toString())
			.body(AccountResponse.of(result.tenant()));
	}

	@Operation(summary = "로그아웃", description = "쿠키를 지운다(204)")
	@SecurityRequirements
	@PostMapping("/auth/logout")
	public ResponseEntity<Void> logout() {
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, tokens.logoutCookie().toString()).build();
	}

	@Operation(summary = "로그인한 계정 (헤더 · 설정 · 신청자 표시)")
	@GetMapping("/me")
	public AccountResponse me(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
		return AccountResponse.of(authService.currentTenant(CurrentTenant.id(jwt)));
	}

}
