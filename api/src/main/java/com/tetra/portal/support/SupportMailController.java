package com.tetra.portal.support;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tetra.portal.common.error.ApiErrorCodes;
import com.tetra.portal.common.error.ApiException;
import com.tetra.portal.common.error.ErrorCode;
import com.tetra.portal.config.TetraProperties;
import com.tetra.portal.external.MailGateway;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * UI-TP-03 문의 메일(로그인 전 — 공개). 받는 곳은 운영팀 주소 하나(MAIL_SUPPORT_TO)로 고정이라 임의 주소로 보내는 데 쓸 수 없다.
 * 답장은 보낸 사람(from)에게 가도록 Reply-To로 넣는다.
 * TODO(계획서 Q13): 받는 주소 확정 · SES 인증. 남용(같은 IP 반복)은 지금 WAF 레이트 리밋(기록만) 뿐 — Block 전환은 운영 후.
 */
@Tag(name = "문의", description = "UI-TP-03")
@RestController
@RequestMapping("/api/portal/support-mail")
public class SupportMailController {

	private final MailGateway mail;

	private final TetraProperties properties;

	public SupportMailController(MailGateway mail, TetraProperties properties) {
		this.mail = mail;
		this.properties = properties;
	}

	public record SupportMailRequest(
			@Schema(description = "회신받을 이메일") @NotBlank(message = "필수 입력입니다.") @Email(message = "이메일 형식이 아닙니다.") @Size(max = 254, message = "254자 이하로 입력해 주세요.") String from,
			@Schema(example = "비밀번호 문의") @NotBlank(message = "필수 입력입니다.") @Size(max = 100, message = "100자 이하로 입력해 주세요.") String subject,
			@NotBlank(message = "필수 입력입니다.") @Size(max = 2000, message = "2000자 이하로 입력해 주세요.") String body) {
	}

	@Operation(summary = "문의 메일 보내기", description = "운영팀 주소로 보낸다(204). SES가 받지 않으면 503")
	@SecurityRequirements
	@ApiErrorCodes({ ErrorCode.INVALID_INPUT, ErrorCode.MAIL_SEND_FAILED })
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Void> send(@Valid @RequestBody SupportMailRequest request) {
		// 제목 · 본문의 줄바꿈 등으로 헤더를 꾸미지 못하게 제목은 한 줄로
		String subject = "[Portal 문의] " + request.subject().replaceAll("[\\r\\n]+", " ").strip();
		String body = "보낸 사람: " + request.from() + "\n\n" + request.body();
		if (!mail.send(new MailGateway.Mail(properties.mail().supportTo(), request.from(), subject, body))) {
			throw new ApiException(ErrorCode.MAIL_SEND_FAILED);
		}
		return ResponseEntity.noContent().build();
	}

}
