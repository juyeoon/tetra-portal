package com.tetra.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.tetra.portal.support.IntegrationTest;
import com.tetra.portal.support.TestData;

/** UI-TP-13 알림센터 목록 — 서버 페이지 · 필터 · 검색 · 건수 · 모두 읽음. */
class NotificationListApiTest extends IntegrationTest {

	/** A사로 목록 요청. params = 이름, 값, 이름, 값 … */
	private ResultActions list(String... params) throws Exception {
		MockHttpServletRequestBuilder request = get("/api/portal/notifications").cookie(loginCookie(A));
		for (int i = 0; i < params.length; i += 2) {
			request.param(params[i], params[i + 1]);
		}
		return mvc.perform(request);
	}

	private DocumentContext json(String... params) throws Exception {
		return JsonPath.parse(list(params).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8));
	}

	/** 보이는 항목 — 내용의 첫 낱말(r1 · r2 …), 목록 순서대로 */
	private List<String> visible(String... params) throws Exception {
		List<String> contents = json(params).read("$.items[*].content");
		return contents.stream().map(content -> content.split(" ")[0]).toList();
	}

	/** 건수 표 — kind = priority · chip */
	private static Map<String, Object> counts(DocumentContext doc, String kind) {
		return doc.read("$.counts." + kind);
	}

	/**
	 * 필터 · 건수용 6건(최신순 = 이 순서).
	 * r1 긴급 · 안읽음 · 이벤트 칩 / r2 주의 · 읽음 · 신청 / r3 정보 · 안읽음 · 신청 / r4 정보 · 안읽음 · 리포트 /
	 * r5 주의 · 안읽음 · 칩 없음 / r6 정보 · 읽음 · 이벤트 칩
	 */
	private void sixRows() {
		data.tenant(A, "A사", "ACTIVE", "READY");
		data.event(1001, A).insert();
		data.notification(A, 1001L, "HOSTING", "URGENT", TestData.BASE.plusMinutes(6), "r1 100% 할인 [Fall]", false);
		data.notification(A, 1001L, "APPLICATION", "WARNING", TestData.BASE.plusMinutes(5), "r2 1000 할인", true);
		data.notification(A, 1001L, "APPLICATION", "INFO", TestData.BASE.plusMinutes(4), "r3 a_b 안내", false);
		data.notification(A, null, "REPORT", "INFO", TestData.BASE.plusMinutes(3), "r4 axb 안내!", false);
		data.notification(A, null, "SYSTEM", "WARNING", TestData.BASE.plusMinutes(2), "r5 점검 100%", false);
		data.notification(A, 1001L, "EVENT", "INFO", TestData.BASE.plusMinutes(1), "r6 fall sale", true);
	}

	// ---------------------------------------------------------------- 페이지

	@Test
	void pagesAreNewestFirstWithIdTieBreak() throws Exception {
		data.tenant(A, "A사", "ACTIVE", "READY");
		List<Long> ids = new ArrayList<>();
		for (int i = 0; i < 25; i++) {
			// 두 건씩 같은 시각 — 같으면 나중 알림(id 큰 것) 먼저
			ids.add(data.notification(A, null, "INFO", TestData.BASE.plusMinutes(i / 2), "알림 " + i, false));
		}

		// 기본 10건 — 25건이면 3페이지
		list().andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(10))
			.andExpect(jsonPath("$.items[0].notificationId").value(ids.get(24)))
			.andExpect(jsonPath("$.items[1].notificationId").value(ids.get(23)))
			.andExpect(jsonPath("$.items[9].notificationId").value(ids.get(15)))
			.andExpect(jsonPath("$.page").value(1))
			.andExpect(jsonPath("$.size").value(10))
			.andExpect(jsonPath("$.totalCount").value(25))
			.andExpect(jsonPath("$.totalPages").value(3));
		list("page", "2").andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(10))
			.andExpect(jsonPath("$.items[0].notificationId").value(ids.get(14)))
			.andExpect(jsonPath("$.items[9].notificationId").value(ids.get(5)))
			.andExpect(jsonPath("$.page").value(2));
		list("page", "3").andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(5))
			.andExpect(jsonPath("$.items[0].notificationId").value(ids.get(4)))
			.andExpect(jsonPath("$.items[4].notificationId").value(ids.get(0)))
			.andExpect(jsonPath("$.page").value(3))
			.andExpect(jsonPath("$.totalPages").value(3));
		// 범위 밖 — 빈 목록, 전체 수 · 페이지 수는 그대로(화면이 마지막 페이지로 고친다)
		list("page", "4").andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(0))
			.andExpect(jsonPath("$.page").value(4))
			.andExpect(jsonPath("$.totalCount").value(25))
			.andExpect(jsonPath("$.totalPages").value(3));
		// size를 주면 그 크기로
		list("page", "2", "size", "20").andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(5))
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.totalPages").value(2));
	}

	@Test
	void invalidParametersAreRejected() throws Exception {
		data.tenant(A, "A사", "ACTIVE", "READY");
		String[][] cases = { { "page", "0" }, { "page", "10001" }, { "page", "abc" }, { "size", "0" }, { "size", "51" },
				{ "priority", "urgent" }, { "priority", "ALL" }, { "chip", "TENANT" }, { "unreadOnly", "maybe" },
				{ "q", "가".repeat(101) } };
		for (String[] c : cases) {
			list(c).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.fields." + c[0]).exists());
		}
		// 경계 — 100자(앞뒤 공백은 빼고 센다) · 50건 · 빈 값(= 전체)은 받는다
		list("q", "  " + "가".repeat(100) + "  ", "size", "50", "priority", "", "chip", "", "page", "10000")
			.andExpect(status().isOk());
	}

	// ---------------------------------------------------------------- 필터 · 검색

	@Test
	void filtersAreCombinedWithAnd() throws Exception {
		sixRows();

		assertThat(visible()).containsExactly("r1", "r2", "r3", "r4", "r5", "r6");
		assertThat(visible("unreadOnly", "true")).containsExactly("r1", "r3", "r4", "r5");
		assertThat(visible("priority", "INFO")).containsExactly("r3", "r4", "r6");
		assertThat(visible("priority", "INFO", "unreadOnly", "true")).containsExactly("r3", "r4");
		assertThat(visible("priority", "WARNING", "chip", "APPLICATION")).containsExactly("r2");
		assertThat(visible("chip", "EVENT")).containsExactly("r1", "r6");
		assertThat(visible("chip", "EVENT", "unreadOnly", "true", "q", "fall")).containsExactly("r1");
		assertThat(visible("priority", "URGENT", "chip", "REPORT")).isEmpty();
	}

	@Test
	void searchIsLiteralTrimmedAndCaseInsensitive() throws Exception {
		sixRows();

		// % · _ · ! 는 글자 그대로(1000 할인 · axb는 아님)
		assertThat(visible("q", "100%")).containsExactly("r1", "r5");
		assertThat(visible("q", "a_b")).containsExactly("r3");
		assertThat(visible("q", "!")).containsExactly("r4");
		assertThat(visible("q", "FALL")).containsExactly("r1", "r6");
		assertThat(visible("q", "  fall  ")).containsExactly("r1", "r6");
		assertThat(visible("q", "   ")).containsExactly("r1", "r2", "r3", "r4", "r5", "r6");
	}

	@Test
	void chipFilterFollowsNotificationRulesForEveryCategory() throws Exception {
		data.tenant(A, "A사", "ACTIVE", "READY");
		data.event(1001, A).insert();
		// 내용 = 줄 이름. 첫 줄을 가장 늦은 시각으로 넣어 목록 순서 = 이 배열 순서
		String[][] rows = {
				// 이름, category, eventId
				{ "a1", "APPLICATION", "1001" }, { "a2", "APPLICATION", null }, { "h1", "HOSTING", "1001" },
				{ "e1", "EVENT", "1001" }, { "e2", "EVENT", null }, { "m1", "MAIL", "1001" }, { "m2", "MAIL", null },
				{ "p1", "REPORT", null }, { "d1", "DELAY", "1001" }, { "s1", "SYSTEM", null }, { "x1", "SLACK", "1001" } };
		for (int i = 0; i < rows.length; i++) {
			Long eventId = rows[i][2] == null ? null : Long.valueOf(rows[i][2]);
			data.notification(A, eventId, rows[i][1], "INFO", TestData.BASE.plusMinutes(rows.length - i),
					rows[i][0] + " " + rows[i][1], false);
		}

		// 메일은 이벤트가 있을 때만 이벤트, 운영자 전용 · 모르는 값은 분류 '전체'에서만
		assertThat(visible("chip", "APPLICATION")).containsExactly("a1", "a2");
		assertThat(visible("chip", "EVENT")).containsExactly("h1", "e1", "e2", "m1");
		assertThat(visible("chip", "REPORT")).containsExactly("p1");
		assertThat(json().read("$.totalCount", Integer.class)).isEqualTo(rows.length);
		// 건수도 같은 규칙
		assertThat(counts(json(), "chip"))
			.isEqualTo(Map.of("ALL", rows.length, "APPLICATION", 2, "EVENT", 4, "REPORT", 1));
	}

	// ---------------------------------------------------------------- 건수

	@Test
	void countsFollowScreenRules() throws Exception {
		sixRows();

		// 전체: 우선순위 1 · 2 · 3, 분류 신청 2(r2 · r3) · 이벤트 2(r1 · r6) · 리포트 1(r4) — r5(칩 없음)는 전체에만
		DocumentContext all = json();
		assertThat(all.read("$.unreadCount", Integer.class)).isEqualTo(4);
		assertThat(counts(all, "priority")).isEqualTo(Map.of("ALL", 6, "URGENT", 1, "WARNING", 2, "INFO", 3));
		assertThat(counts(all, "chip")).isEqualTo(Map.of("ALL", 6, "APPLICATION", 2, "EVENT", 2, "REPORT", 1));

		// 안 읽은 것만 — 우선순위 · 분류 건수 모두 따른다
		DocumentContext unread = json("unreadOnly", "true");
		assertThat(counts(unread, "priority")).isEqualTo(Map.of("ALL", 4, "URGENT", 1, "WARNING", 1, "INFO", 2));
		assertThat(counts(unread, "chip")).isEqualTo(Map.of("ALL", 4, "APPLICATION", 1, "EVENT", 1, "REPORT", 1));

		// 우선순위는 분류 건수만 바꾸고, 고른 분류 · 검색은 어느 건수도 바꾸지 않는다(안 읽은 수는 조건과 무관)
		String[][] extras = { {}, { "chip", "EVENT" }, { "q", "없는 말" }, { "chip", "EVENT", "q", "없는 말" } };
		for (String[] extra : extras) {
			List<String> params = new ArrayList<>(List.of("unreadOnly", "true", "priority", "INFO"));
			params.addAll(List.of(extra));
			DocumentContext doc = json(params.toArray(String[]::new));
			assertThat(doc.read("$.unreadCount", Integer.class)).as(params.toString()).isEqualTo(4);
			assertThat(counts(doc, "priority")).as(params.toString())
				.isEqualTo(Map.of("ALL", 4, "URGENT", 1, "WARNING", 1, "INFO", 2));
			assertThat(counts(doc, "chip")).as(params.toString())
				.isEqualTo(Map.of("ALL", 2, "APPLICATION", 1, "EVENT", 0, "REPORT", 1));
		}
		list("unreadOnly", "true", "priority", "INFO", "chip", "EVENT", "q", "없는 말")
			.andExpect(jsonPath("$.items.length()").value(0))
			.andExpect(jsonPath("$.totalCount").value(0))
			.andExpect(jsonPath("$.totalPages").value(0));

		// 칩 없는 알림(r5)만 남는 조건 — 분류는 전체에만 센다
		assertThat(counts(json("unreadOnly", "true", "priority", "WARNING"), "chip"))
			.isEqualTo(Map.of("ALL", 1, "APPLICATION", 0, "EVENT", 0, "REPORT", 0));
	}

	// ---------------------------------------------------------------- 모두 읽음

	@Test
	void readAllMarksOnlyThisTenantsUnreadNotifications() throws Exception {
		data.tenant(A, "A사", "ACTIVE", "READY");
		data.tenant(B, "B사", "ACTIVE", "READY");
		for (int i = 0; i < 3; i++) {
			data.notification(A, null, "INFO", TestData.BASE.plusMinutes(i), "A 안읽음 " + i, false);
		}
		data.notification(A, null, "INFO", TestData.BASE, "A 읽음", true);
		data.notification(B, null, "INFO", TestData.BASE, "B 안읽음 1", false);
		data.notification(B, null, "URGENT", TestData.BASE, "B 안읽음 2", false);

		mvc.perform(patch("/api/portal/notifications/read-all").cookie(loginCookie(A)))
			.andExpect(status().isNoContent());

		assertThat(data.count("SELECT COUNT(*) FROM t_" + A + ".tenant_notification WHERE is_read = 0")).isZero();
		assertThat(data.count("SELECT COUNT(*) FROM t_" + A + ".tenant_notification WHERE is_read = 1")).isEqualTo(4);
		// 다른 테넌트 알림은 그대로
		assertThat(data.count("SELECT COUNT(*) FROM t_" + B + ".tenant_notification WHERE is_read = 0")).isEqualTo(2);
		list("unreadOnly", "true").andExpect(jsonPath("$.items.length()").value(0))
			.andExpect(jsonPath("$.unreadCount").value(0));

		// 이미 다 읽었어도 204
		mvc.perform(patch("/api/portal/notifications/read-all").cookie(loginCookie(A)))
			.andExpect(status().isNoContent());
		assertThat(data.count("SELECT COUNT(*) FROM t_" + A + ".tenant_notification WHERE is_read = 1")).isEqualTo(4);
	}

	// ---------------------------------------------------------------- 안 읽은 수 (사이드바 배지)

	private long unreadCount(String tenantId) throws Exception {
		String body = mvc.perform(get("/api/portal/notifications/unread-count").cookie(loginCookie(tenantId)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
		return JsonPath.parse(body).read("$.count", Long.class);
	}

	@Test
	void unreadCountCountsOnlyThisTenant() throws Exception {
		data.tenant(A, "A사", "ACTIVE", "READY");
		data.tenant(B, "B사", "ACTIVE", "READY");
		assertThat(unreadCount(A)).isZero();

		for (int i = 0; i < 3; i++) {
			data.notification(A, null, "INFO", TestData.BASE.plusMinutes(i), "A 안읽음 " + i, false);
		}
		data.notification(A, null, "URGENT", TestData.BASE, "A 읽음", true);
		data.notification(B, null, "INFO", TestData.BASE, "B 안읽음 1", false);
		data.notification(B, null, "WARNING", TestData.BASE, "B 안읽음 2", false);

		assertThat(unreadCount(A)).isEqualTo(3);
		assertThat(unreadCount(B)).isEqualTo(2);
	}

	@Test
	void unreadCountDropsAfterReadAndReadAllAndMatchesList() throws Exception {
		sixRows(); // 안 읽음 r1 · r3 · r4 · r5
		long r1 = json("q", "r1").read("$.items[0].notificationId", Long.class);

		assertThat(unreadCount(A)).isEqualTo(4);
		// 목록 unreadCount와 같은 값 — 목록 조건과 무관
		for (String[] params : new String[][] { {}, { "unreadOnly", "true", "priority", "INFO" }, { "chip", "REPORT", "q", "없는 말" } }) {
			assertThat(json(params).read("$.unreadCount", Long.class)).as(List.of(params).toString()).isEqualTo(4L);
		}

		mvc.perform(patch("/api/portal/notifications/{id}/read", r1).cookie(loginCookie(A))).andExpect(status().isNoContent());
		assertThat(unreadCount(A)).isEqualTo(3);
		assertThat(json().read("$.unreadCount", Long.class)).isEqualTo(3L);

		mvc.perform(patch("/api/portal/notifications/read-all").cookie(loginCookie(A))).andExpect(status().isNoContent());
		assertThat(unreadCount(A)).isZero();
		assertThat(json().read("$.unreadCount", Long.class)).isZero();
	}

}
