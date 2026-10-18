package com.tetra.portal.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.tetra.portal.common.error.ApiErrorCodes;
import com.tetra.portal.common.error.ErrorCode;
import com.tetra.portal.common.error.ErrorResponse;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;

/**
 * 프런트용 API 문서(springdoc). /v3/api-docs(JSON), /swagger-ui.html(화면).
 * API_DOCS_ENABLED=true일 때만 열린다(기본 꺼짐, application.yml). 문서화 대상은 /api/**.
 * 인증 쿠키 설명은 P1(로그인)에서 붙인다.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

	static final String ERROR_SCHEMA = "ErrorResponse";

	private static final String DESCRIPTION = """
			Tetra Portal(고객사 관리자용) API. 화면은 같은 출처의 상대 경로 `/api`로 호출합니다.

			**공통 형식**
			- 일시: `+09:00`이 붙은 ISO-8601 문자열 (예: `2026-10-14T14:02:00+09:00`). 화면 표시 형식은 화면에서 바꿉니다
			- 상태 값: DB 값(영문 UPPER_SNAKE_CASE) 그대로. 한글 표기는 화면에서 바꿉니다
			- 값이 없으면 `null`로 내려갑니다(필드는 빠지지 않음)

			**오류 응답**: 모든 오류는 `ErrorResponse` 형식입니다. `code`로 분기하고 `message`는 그대로 보여 줘도 됩니다.
			400이면 `fields`에 항목별 사유가 들어갑니다.
			""";

	@Bean
	OpenAPI tetraOpenApi() {
		return new OpenAPI().info(new Info().title("Tetra Portal API").version("v1").description(DESCRIPTION));
	}

	/** ErrorResponse 스키마를 components에 등록한다. */
	@Bean
	OpenApiCustomizer errorSchemaCustomizer() {
		return openApi -> {
			@SuppressWarnings("rawtypes")
			Map<String, Schema> schemas = ModelConverters.getInstance().read(ErrorResponse.class);
			if (openApi.getComponents() == null) {
				openApi.components(new io.swagger.v3.oas.models.Components());
			}
			schemas.forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));
		};
	}

	/** 오류 응답을 상태 코드별로 붙인다. @ApiErrorCodes에 적은 code + 모든 API에 500. */
	@Bean
	OperationCustomizer errorCodeCustomizer() {
		return (operation, handlerMethod) -> {
			List<ErrorCode> codes = new ArrayList<>();
			ApiErrorCodes declared = handlerMethod.getMethodAnnotation(ApiErrorCodes.class);
			if (declared != null) {
				codes.addAll(List.of(declared.value()));
			}
			codes.add(ErrorCode.INTERNAL_ERROR);

			Map<Integer, List<ErrorCode>> byStatus = codes.stream()
				.distinct()
				.collect(Collectors.groupingBy(c -> c.status().value(), TreeMap::new, Collectors.toList()));
			Map<String, ApiResponse> added = new LinkedHashMap<>();
			byStatus.forEach((status, list) -> {
				String description = list.stream()
					.map(c -> "`" + c.name() + "` — " + c.message())
					.collect(Collectors.joining("<br>"));
				added.put(String.valueOf(status), new ApiResponse().description(description)
					.content(new Content().addMediaType("application/json",
							new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA)))));
			});
			added.forEach(operation.getResponses()::addApiResponse);
			return operation;
		};
	}

}
