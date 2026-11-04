package com.tetra.portal.common.audit;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.core.json.JsonWriteFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 감사 줄 직렬화 전용 매퍼. API 응답용 매퍼(Boot 자동 설정)와 섞지 않는다.
 * - 들여쓰기 없음 + 문자열 안의 줄바꿈 · 제어 문자는 Jackson이 \n · \\uXXXX로 → 늘 한 줄
 * - ESCAPE_NON_ASCII: 한글 등 ASCII 밖 문자도 \\uXXXX로 → 컨테이너 stdout 문자셋과 상관없이 ASCII만 나간다
 *   (CloudWatch JSON 필터는 이스케이프를 풀어서 본다)
 * - null 필드는 빼되, AuditEvent에서 ALWAYS로 둔 actor · target · tenant_id는 null로 남긴다
 * - 줄 상한 8KB(Docker 로그 드라이버가 16KB 넘는 줄을 쪼갬) — 넘으면 changes를 비우고 truncated:true.
 *   changes 말고는 필드마다 상한이 있어(UA 512 · id 64 · 가린 이메일) 최악으로 모두 \\uXXXX여도 5KB 아래다.
 */
public final class AuditJson {

	public static final int MAX_LINE_BYTES = 8 * 1024;

	private static final JsonMapper MAPPER = JsonMapper.builder()
		.enable(JsonWriteFeature.ESCAPE_NON_ASCII)
		.disable(SerializationFeature.INDENT_OUTPUT)
		.changeDefaultPropertyInclusion(
				inclusion -> JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
		.build();

	private AuditJson() {
	}

	/** 한 줄(ASCII). 줄바꿈 문자는 붙이지 않는다 — 로그 패턴(%msg%n)이 붙인다. */
	public static String line(AuditEvent event) {
		String line = MAPPER.writeValueAsString(event);
		// ASCII만 나가므로 글자 수 = 바이트 수
		if (line.length() > MAX_LINE_BYTES && event.changes() != null) {
			line = MAPPER.writeValueAsString(event.withoutChanges());
		}
		return line;
	}

}
