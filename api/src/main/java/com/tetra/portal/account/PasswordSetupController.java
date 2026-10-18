package com.tetra.portal.account;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tetra.portal.common.error.ApiErrorCodes;
import com.tetra.portal.common.error.ErrorCode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * UI-TP-04 비밀번호 설정 (로그인 전 — 공개). 화면은 주소의 t · exp · sig를 그대로 본문에 담아 보낸다.
 * 서명 값이 접근 로그 · 브라우저 기록에 한 번 더 남지 않도록 확인도 GET이 아니라 POST로 받는다.
 */
@Tag(name = "비밀번호 설정", description = "UI-TP-04 — Console이 보낸 메일 링크(?t=&exp=&sig=)로 진입")
@RestController
@RequestMapping("/api/portal/password/setup")
public class PasswordSetupController {

	private final PasswordSetupService service;

	public PasswordSetupController(PasswordSetupService service) {
		this.service = service;
	}

	public record LinkRequest(
			@Schema(description = "주소의 t (tenant_id)", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank(message = "필수 입력입니다.") String t,
			@Schema(description = "주소의 exp (만료 epoch초)", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull(message = "필수 입력입니다.") Long exp,
			@Schema(description = "주소의 sig", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank(message = "필수 입력입니다.") String sig) {

		@Override
		public String toString() {
			return "LinkRequest[t=" + t + ", exp=" + exp + ", sig=****]";
		}

	}

	public record LinkResponse(@Schema(description = "설정할 계정 이메일 (화면 표시)") String email) {
	}

	// TODO(확정 필요): 비밀번호 문자 조합 규칙. 지금은 화면과 같이 8자 이상만. 상한 64는 BCrypt 72바이트 제한 안쪽
	public record SetupRequest(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank(message = "필수 입력입니다.") String t,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull(message = "필수 입력입니다.") Long exp,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank(message = "필수 입력입니다.") String sig,
			@Schema(description = "새 비밀번호 8~64자", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank(message = "필수 입력입니다.") @Size(min = 8, max = 64, message = "8자 이상 64자 이하로 입력해 주세요.") String password) {

		@Override
		public String toString() {
			return "SetupRequest[t=" + t + ", exp=" + exp + ", sig=****, password=****]";
		}

	}

	@Operation(summary = "링크 확인", description = "화면 진입 때 호출. 맞으면 이메일, 아니면 410(만료 화면)")
	@SecurityRequirements
	@ApiErrorCodes({ ErrorCode.INVALID_INPUT, ErrorCode.PASSWORD_LINK_INVALID })
	@PostMapping(path = "/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
	public LinkResponse verify(@Valid @RequestBody LinkRequest request) {
		return new LinkResponse(service.verify(request.t(), request.exp(), request.sig()));
	}

	@Operation(summary = "비밀번호 설정", description = "성공 204 → 화면은 로그인으로. 같은 링크는 다시 쓸 수 없다(410)")
	@SecurityRequirements
	@ApiErrorCodes({ ErrorCode.INVALID_INPUT, ErrorCode.PASSWORD_LINK_INVALID })
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Void> setup(@Valid @RequestBody SetupRequest request) {
		service.setup(request.t(), request.exp(), request.sig(), request.password());
		return ResponseEntity.noContent().build();
	}

}
