package com.tetra.portal.common.error;

import org.springframework.http.HttpStatus;

/**
 * 오류 code 값을 모으는 곳. message는 화면에 그대로 보여도 되는 문구만 둔다.
 * 같은 뜻의 code는 Console API와 이름을 맞춘다(화면이 같은 값으로 분기).
 */
public enum ErrorCode {

	// 400
	INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력 값을 확인해 주세요."),

	// 401
	UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
	LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "계정 정보를 찾을 수 없습니다."),

	// 403
	FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

	// 404
	NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."),
	TENANT_NOT_FOUND(HttpStatus.NOT_FOUND, "테넌트를 찾을 수 없습니다."),
	EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "이벤트를 찾을 수 없습니다."),
	NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),
	REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "리포트를 찾을 수 없습니다."),

	// 405
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 메서드입니다."),

	// 415 — 상태를 바꾸는 API는 JSON만 (다른 사이트의 form 전송 차단)
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),

	// 409
	TENANT_NOT_READY(HttpStatus.CONFLICT, "테넌트 환경 준비가 끝나지 않았습니다."),
	RESULT_NOT_READY(HttpStatus.CONFLICT, "이벤트 결과가 아직 없습니다."),

	// 410 — 비밀번호 설정 링크: 만료 · 이미 사용 · 위조를 구분하지 않는다(UI-TP-04 만료 화면 하나)
	PASSWORD_LINK_INVALID(HttpStatus.GONE, "링크가 만료되었거나 이미 사용되었습니다."),

	// 500
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "처리 중 오류가 발생했습니다."),

	// 503 — SES가 발송을 받지 않음(권한 · 샌드박스 미인증 주소 등). 잠시 뒤 다시 시도
	MAIL_SEND_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "메일을 보내지 못했습니다. 잠시 뒤 다시 시도해 주세요.");

	private final HttpStatus status;

	private final String message;

	ErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	public HttpStatus status() {
		return status;
	}

	public String message() {
		return message;
	}

}
