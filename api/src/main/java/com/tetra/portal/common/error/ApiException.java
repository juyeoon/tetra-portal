package com.tetra.portal.common.error;

import java.util.Map;

/** 예상한 오류. 메시지에 비밀 값(개인키·링크·비밀번호·JWT)을 넣지 않는다. */
public class ApiException extends RuntimeException {

	private final ErrorCode code;

	private final Map<String, String> fields;

	public ApiException(ErrorCode code) {
		this(code, Map.of());
	}

	public ApiException(ErrorCode code, Map<String, String> fields) {
		super(code.name());
		this.code = code;
		this.fields = fields;
	}

	public static ApiException invalid(String field, String reason) {
		return new ApiException(ErrorCode.INVALID_INPUT, Map.of(field, reason));
	}

	public ErrorCode code() {
		return code;
	}

	public Map<String, String> fields() {
		return fields;
	}

}
