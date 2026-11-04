package com.tetra.portal.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 감사 줄 부품 — 가림 · ip · request_id · 직렬화(한 줄 · 순서 · 8KB). 값은 모두 가짜. */
class AuditPartsTest {

	static final JsonMapper JSON = JsonMapper.builder().build();

	static AuditEvent event(Map<String, AuditEvent.Change> changes, String userAgent) {
		return new AuditEvent("AUDIT", "00000000-0000-4000-8000-000000000000", "2026-11-04T14:03:21.512+09:00", "portal",
				new AuditEvent.Party("TENANT", "aaaaaaaaaaaa"), "203.0.113.7", userAgent, "SETTINGS_EMAIL_CHANGE",
				new AuditEvent.Party("TENANT", "aaaaaaaaaaaa"), "aaaaaaaaaaaa", null, "SUCCESS", null, changes, null,
				null, "1-67891233-abcdef012345678912345678");
	}

	// ---------------------------------------------------------------- 가림

	@Test
	void emailKeepsTwoLettersAndTopLevelDomain() {
		assertThat(Masking.email("kim@example.com")).isEqualTo("ki***@ex***.com");
		assertThat(Masking.email("  Kim.Lee@Mail.Example.co.kr ")).isEqualTo("Ki***@Ma***.kr");
		// 짧은 부분은 전체가 드러나지 않게
		assertThat(Masking.email("ab@cd.io")).isEqualTo("a***@c***.io");
		assertThat(Masking.email("a@b.com")).isEqualTo("***@***.com");
		assertThat(Masking.email("no-at-sign")).isEqualTo("no***");
		assertThat(Masking.email("user@localhost")).isEqualTo("us***@lo***");
		assertThat(Masking.email("user@example." + "x".repeat(100))).endsWith("." + "x".repeat(16));
		assertThat(Masking.email("😀😀😀@예시.한국")).isEqualTo("😀😀***@예***.한국"); // 서로게이트 쌍을 자르지 않음
		assertThat(Masking.email(null)).isNull();
	}

	@Test
	void nameKeepsFirstAndLastLetter() {
		assertThat(Masking.name("박민호")).isEqualTo("박*호");
		assertThat(Masking.name("남궁민수")).isEqualTo("남**수");
		assertThat(Masking.name("이호")).isEqualTo("이*");
		assertThat(Masking.name("김")).isEqualTo("*");
		assertThat(Masking.name(" ")).isEmpty();
		assertThat(Masking.name(null)).isNull();
	}

	// ---------------------------------------------------------------- ip

	@Test
	void clientIpIsRightMostForwardedValueOrRemoteAddr() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("10.0.1.20");
		assertThat(ClientIp.resolve(request)).isEqualTo("10.0.1.20");

		request.addHeader("X-Forwarded-For", "1.1.1.1, 10.0.0.5");
		assertThat(ClientIp.resolve(request)).isEqualTo("10.0.0.5");

		MockHttpServletRequest twoHeaders = new MockHttpServletRequest();
		twoHeaders.addHeader("X-Forwarded-For", "6.6.6.6");
		twoHeaders.addHeader("X-Forwarded-For", "7.7.7.7, 203.0.113.7");
		assertThat(ClientIp.resolve(twoHeaders)).isEqualTo("203.0.113.7");

		MockHttpServletRequest ipv6 = new MockHttpServletRequest();
		ipv6.addHeader("X-Forwarded-For", "2001:db8::1");
		assertThat(ClientIp.resolve(ipv6)).isEqualTo("2001:db8::1");

		MockHttpServletRequest trailingComma = new MockHttpServletRequest();
		trailingComma.setRemoteAddr("10.0.1.21");
		trailingComma.addHeader("X-Forwarded-For", "1.1.1.1, ");
		assertThat(ClientIp.resolve(trailingComma)).isEqualTo("10.0.1.21");

		MockHttpServletRequest huge = new MockHttpServletRequest();
		huge.addHeader("X-Forwarded-For", "x".repeat(500));
		assertThat(ClientIp.resolve(huge)).hasSize(64);
	}

	// ---------------------------------------------------------------- request_id

	@Test
	void requestIdPrefersSelfThenRootElseUuid() {
		String root = "1-67891233-abcdef012345678912345678";
		String self = "1-67891234-12456789abcdef0123456789";
		assertThat(RequestIds.resolve("Root=" + root + ";Self=" + self)).isEqualTo(self);
		assertThat(RequestIds.resolve("Self=" + self + "; Root=" + root + "; Sampled=1")).isEqualTo(self);
		assertThat(RequestIds.resolve("Root=" + root)).isEqualTo(root);
		assertThat(RequestIds.resolve("Root=" + root.toUpperCase())).matches("^[0-9a-f-]{36}$");
		assertThat(RequestIds.resolve("Root=" + root + "\n")).isEqualTo(root); // 끝 공백 · 줄바꿈은 잘라 본다
		assertThat(RequestIds.resolve("Root=" + root + "x")).matches("^[0-9a-f-]{36}$");
		assertThat(RequestIds.resolve("garbage")).matches("^[0-9a-f-]{36}$");
		assertThat(RequestIds.resolve(null)).matches("^[0-9a-f-]{36}$");
		assertThat(RequestIds.resolve(null)).isNotEqualTo(RequestIds.resolve(null));
	}

	// ---------------------------------------------------------------- 직렬화

	@Test
	void lineIsOneAsciiLineInFixedKeyOrder() {
		Map<String, AuditEvent.Change> changes = new LinkedHashMap<>();
		changes.put("email_notification_enabled", new AuditEvent.Change(false, true));
		changes.put("contact_name", new AuditEvent.Change(null, Masking.name("박민호")));
		String line = AuditJson.line(event(changes, "브라우저\n\t\u0000\u001F\u007F  \"끝\""));

		assertThat(line).doesNotContain("\n").doesNotContain("\r");
		assertThat(line.chars().allMatch(c -> c >= 0x20 && c < 0x80)).isTrue();
		assertThat(line).startsWith(
				"{\"log_type\":\"AUDIT\",\"audit_id\":\"00000000-0000-4000-8000-000000000000\",\"time\":\"2026-11-04T14:03:21.512+09:00\","
						+ "\"app\":\"portal\",\"actor\":{\"type\":\"TENANT\",\"id\":\"aaaaaaaaaaaa\"},\"ip\":\"203.0.113.7\",\"user_agent\":");
		assertThat(line).endsWith(",\"action\":\"SETTINGS_EMAIL_CHANGE\",\"target\":{\"type\":\"TENANT\",\"id\":\"aaaaaaaaaaaa\"},"
				+ "\"tenant_id\":\"aaaaaaaaaaaa\",\"result\":\"SUCCESS\",\"changes\":{\"email_notification_enabled\":"
				+ "{\"before\":false,\"after\":true},\"contact_name\":{\"before\":null,\"after\":\"\\uBC15*\\uD638\"}},"
				+ "\"request_id\":\"1-67891233-abcdef012345678912345678\"}");

		JsonNode parsed = JSON.readTree(line);
		assertThat(parsed.path("user_agent").asString()).isEqualTo("브라우저\n\t\u0000\u001F\u007F  \"끝\"");
		assertThat(parsed.path("changes").path("contact_name").path("after").asString()).isEqualTo("박*호");
	}

	@Test
	void absentOptionalFieldsAreLeftOutButActorTargetTenantStayAsNull() {
		AuditEvent anonymous = new AuditEvent("AUDIT", "id", "t", "portal", null, "203.0.113.7", null, "AUTH_LOGOUT",
				null, null, null, "FAILURE", "UNAUTHENTICATED", null, null, null, "rid");
		assertThat(AuditJson.line(anonymous)).isEqualTo("{\"log_type\":\"AUDIT\",\"audit_id\":\"id\",\"time\":\"t\","
				+ "\"app\":\"portal\",\"actor\":null,\"ip\":\"203.0.113.7\",\"action\":\"AUTH_LOGOUT\",\"target\":null,"
				+ "\"tenant_id\":null,\"result\":\"FAILURE\",\"reason\":\"UNAUTHENTICATED\",\"request_id\":\"rid\"}");
	}

	@Test
	void lineOver8kDropsChangesAndMarksTruncated() {
		Map<String, AuditEvent.Change> changes = new LinkedHashMap<>();
		for (int i = 0; i < 40; i++) {
			// 값 200자 상한(AuditContext.change)을 지나도 필드가 많으면 넘는다 — 한글은 \\uXXXX(6바이트)
			changes.put("field_" + i, new AuditEvent.Change("가".repeat(200), "나".repeat(200)));
		}
		String line = AuditJson.line(event(changes, "a".repeat(512)));
		assertThat(line.length()).isLessThanOrEqualTo(AuditJson.MAX_LINE_BYTES);
		JsonNode parsed = JSON.readTree(line);
		assertThat(parsed.has("changes")).isFalse();
		assertThat(parsed.path("truncated").asBoolean()).isTrue();
		assertThat(List.copyOf(parsed.propertyNames())).containsSubsequence("result", "truncated", "request_id");

		// 상한 안이면 그대로 (truncated 없음)
		String small = AuditJson.line(event(Map.of("a", new AuditEvent.Change(1, 2)), "ua"));
		assertThat(JSON.readTree(small).has("truncated")).isFalse();
	}

	@Test
	void worstCaseWithoutChangesStaysUnder8k() {
		// changes 말고는 필드마다 상한 — 모두 \\uXXXX로 나가는 최악이어도 8KB 아래
		String wide = "\u0001";
		AuditEvent worst = new AuditEvent("AUDIT", java.util.UUID.randomUUID().toString(), "2026-11-04T14:03:21.512+09:00",
				"portal", new AuditEvent.Party("TENANT", wide.repeat(AuditContext.ID_MAX)), wide.repeat(ClientIp.MAX_LENGTH),
				wide.repeat(AuditFilter.USER_AGENT_MAX), "ISSUANCE_CSV_DOWNLOAD",
				new AuditEvent.Party("EVENT", wide.repeat(AuditContext.ID_MAX)), wide.repeat(AuditContext.ID_MAX),
				Long.MAX_VALUE, "FAILURE", "PASSWORD_LINK_INVALID", null, Boolean.TRUE,
				Masking.email(wide.repeat(50) + "@" + wide.repeat(50) + "." + wide.repeat(50)),
				"1-67891233-abcdef012345678912345678");
		assertThat(AuditJson.line(worst).length()).isLessThan(AuditJson.MAX_LINE_BYTES);
	}

	@Test
	void contextOutsideRequestIsNoOp() {
		// 스케줄러 · 배너 업로드 스레드 — 요청이 없으면 아무것도 하지 않고 예외도 없다
		AuditContext none = AuditContext.current();
		none.tenant("aaaaaaaaaaaa").event(1L).reason("X").loginAttempt("kim@example.com").change("f", 1, 2);
		assertThat(none.actor()).isNull();
		assertThat(none.eventId()).isNull();
		assertThat(none.changes()).isNull();
		assertThat(AuditContext.current()).isSameAs(none);
	}

	@Test
	void changeKeepsOnlyChangedFieldsWithValueLimit() {
		AuditContext context = AuditContext.start(new MockHttpServletRequest());
		context.change("same", true, true).change("text", "a".repeat(10), "b".repeat(500)).change("none", null, null);
		assertThat(context.changes()).containsOnlyKeys("text");
		assertThat((String) context.changes().get("text").after()).hasSize(200);
	}

}
