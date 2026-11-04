package com.tetra.portal.common.error;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.tetra.portal.common.audit.AuditContext;

/** 오류를 ErrorResponse 형식으로 바꾼다. 500에는 내부 정보(SQL, 스택)를 넣지 않는다. */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
		return respond(ex.code(), ex.fields());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> handleInvalid(MethodArgumentNotValidException ex) {
		Map<String, String> fields = new LinkedHashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			fields.putIfAbsent(error.getField(), error.getDefaultMessage());
		}
		ex.getBindingResult()
			.getGlobalErrors()
			.forEach(error -> fields.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
		return respond(ErrorCode.INVALID_INPUT, fields);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
		return respond(ErrorCode.INVALID_INPUT, Map.of("body", "요청 본문 형식이 올바르지 않습니다."));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
		return respond(ErrorCode.INVALID_INPUT, Map.of(ex.getParameterName(), "필수 입력입니다."));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return respond(ErrorCode.INVALID_INPUT, Map.of(ex.getName(), "형식이 올바르지 않습니다."));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
		return respond(ErrorCode.NOT_FOUND, Map.of());
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException ex) {
		return respond(ErrorCode.METHOD_NOT_ALLOWED, Map.of());
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException ex) {
		return respond(ErrorCode.INVALID_INPUT, Map.of("banner", "2MB 이하 파일만 올릴 수 있습니다."));
	}

	@ExceptionHandler(MissingServletRequestPartException.class)
	ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException ex) {
		return respond(ErrorCode.INVALID_INPUT, Map.of(ex.getRequestPartName(), "필수 입력입니다."));
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException ex) {
		return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE, Map.of());
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
		log.error("처리하지 못한 예외", ex);
		return respond(ErrorCode.INTERNAL_ERROR, Map.of());
	}

	private ResponseEntity<ErrorResponse> respond(ErrorCode code, Map<String, String> fields) {
		// 감사 줄의 reason = 응답 본문의 code (감사 대상 요청이 아니면 아무것도 안 함)
		AuditContext.current().reason(code.name());
		return ResponseEntity.status(code.status()).body(ErrorResponse.of(code, fields));
	}

}
