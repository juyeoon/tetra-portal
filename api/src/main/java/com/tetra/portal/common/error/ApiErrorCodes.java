package com.tetra.portal.common.error;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 API가 돌려줄 수 있는 오류 code. API 문서(OpenAPI)에 상태 코드별로 정리해 보여 준다.
 * 401(UNAUTHENTICATED)과 500(INTERNAL_ERROR)은 문서 설정이 자동으로 붙이므로 적지 않는다.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiErrorCodes {

	ErrorCode[] value();

}
