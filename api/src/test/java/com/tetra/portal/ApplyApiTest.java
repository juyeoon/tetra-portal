package com.tetra.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import com.tetra.portal.event.apply.StaleApplicationSweeper;
import com.tetra.portal.external.BannerImage;
import com.tetra.portal.support.IntegrationTest;

/** UI-TP-07~10 이벤트 신청 — WF1-1 (계획서 4-3). */
class ApplyApiTest extends IntegrationTest {

	static final byte[] PNG = pngBytes(1024);

	/** 10일 뒤 10:00 이벤트 2시간, 호스팅 08:00~14:00(6시간). */
	static final LocalDateTime START = LocalDateTime.now().plusDays(10).withHour(10).withMinute(0).withSecond(0).withNano(0);

	@Autowired
	StaleApplicationSweeper sweeper;

	static byte[] pngBytes(int size) {
		byte[] bytes = new byte[size];
		byte[] header = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };
		System.arraycopy(header, 0, bytes, 0, header.length);
		return bytes;
	}

	static String iso(LocalDateTime value) {
		return value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "+09:00";
	}

	static String application(Map<String, String> overrides) {
		String base = """
				{"name":"가을 쿠폰","eventType":"COUPON",
				 "startAt":"%s","endAt":"%s","hostingStartAt":"%s","hostingEndAt":"%s",
				 "trafficPlanCode":"M","stockCount":30000,"endpointUrl":"https://shop.example.com/event",
				 "coupons":[{"name":"10%% 할인","description":"전 상품"},{"name":"무료 배송","description":"3만원 이상"}],
				 "layout":{"title":{"h":"CENTER","v":"TOP"},"banner":{"h":"CENTER","v":"TOP"},
				           "couponList":{"h":"LEFT","v":"MIDDLE"},"issueButton":{"h":"CENTER","v":"BOTTOM"},"theme":"DARK"}}"""
			.formatted(iso(START), iso(START.plusHours(2)), iso(START.minusHours(2)), iso(START.plusHours(4)));
		for (var e : overrides.entrySet()) {
			base = base.replace(e.getKey(), e.getValue());
		}
		return base;
	}

	private ResultActions apply(String tenantId, String json, byte[] banner) throws Exception {
		var request = multipart("/api/portal/events")
			.file(new MockMultipartFile("application", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes()))
			.cookie(loginCookie(tenantId));
		if (banner != null) {
			request.file(new MockMultipartFile("banner", "banner.png", "image/png", banner));
		}
		return mvc.perform(request);
	}

	private long appliedEventId(ResultActions result) throws Exception {
		String body = result.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.applicationStatus").value("APPLYING"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		return Long.parseLong(body.replaceAll(".*\"eventId\":(\\d+).*", "$1"));
	}

	@Test
	void applicationIsAcceptedThenAppliedAfterBannerUpload() throws Exception {
		data.tenant(A, "A사", "INACTIVE", "READY");

		long eventId = appliedEventId(apply(A, application(Map.of()), PNG));

		assertThat(data.awaitApplicationStatus(eventId)).isEqualTo("APPLIED");
		verify(banners).put(eq(A), eq(eventId), any(BannerImage.class));

		Map<String, Object> registry = data.registry(eventId);
		assertThat(registry).containsEntry("tenant_id", A).containsEntry("name", "가을 쿠폰")
			.containsEntry("traffic_plan_code", "M").containsEntry("start_at", START);
		Map<String, Object> event = data.eventRow(A, eventId);
		assertThat(event).containsEntry("application_status", "APPLIED")
			.containsEntry("banner_image_path", "assets/banners/" + eventId + "/banner.png")
			.containsEntry("stock_count", 30000L)
			.containsEntry("hosting_start_at", START.minusHours(2))
			.containsEntry("template_id", 1L);
		assertThat((String) event.get("layout_config")).contains("\"couponList\": {\"h\": \"LEFT\"").contains("\"DARK\"");

		assertThat(data.jdbc().queryForList("SELECT name FROM t_" + A + ".coupon WHERE event_id = ? ORDER BY coupon_id",
				String.class, eventId)).containsExactly("10% 할인", "무료 배송");
		// 예상 비용: 6시간 × (25,000 + 2,000)
		assertThat((BigDecimal) data.jdbc().queryForMap("SELECT * FROM t_" + A + ".event_billing WHERE event_id = ?", eventId)
			.get("estimated_cost")).isEqualByComparingTo("162000");

		List<Map<String, Object>> logs = data.statusLogs(A, eventId);
		assertThat(logs).extracting(l -> l.get("prev_status") + ">" + l.get("new_status"))
			.containsExactly("null>APPLYING", "APPLYING>APPLIED");

		// 첫 신청 성공 → 테넌트 자동 활성화
		assertThat(data.count("SELECT COUNT(*) FROM tetra_platform.tenant WHERE tenant_id = ? AND account_status = 'ACTIVE'", A)).isEqualTo(1);
		assertThat(data.tenantStatusLogs(A)).singleElement()
			.satisfies(l -> assertThat(l).containsEntry("prev_status", "INACTIVE").containsEntry("new_status", "ACTIVE")
				.containsEntry("related_event_id", eventId));

		// 신청 접수 알림 — 운영자(승인 검토가 필요해 WARNING) · 테넌트(INFO) 1건씩. 계정 활성화는 같은 순간이라 따로 남기지 않는다.
		// 운영자 알림은 tenant_id · event_id가 있어야 Console 알림센터 링크가 생긴다
		assertThat(data.operatorNotifications(eventId)).singleElement()
			.satisfies(n -> assertThat(n).containsEntry("tenant_id", A).containsEntry("event_id", eventId)
				.containsEntry("category", "APPLICATION").containsEntry("priority", "WARNING").containsEntry("is_read", false)
				.containsEntry("content", "[A사] [" + eventId + ":가을 쿠폰] 이벤트 신청이 접수되었습니다. 승인 검토가 필요합니다."));
		assertThat(data.tenantNotifications(A, eventId)).singleElement()
			.satisfies(n -> assertThat(n).containsEntry("tenant_id", A).containsEntry("category", "APPLICATION")
				.containsEntry("priority", "INFO").containsEntry("is_read", false)
				.containsEntry("content", "[" + eventId + ":가을 쿠폰] 이벤트 신청이 접수되었습니다."));
		assertThat(data.count("SELECT COUNT(*) FROM tetra_platform.operator_notification WHERE category <> 'APPLICATION'"))
			.isZero();

		// 상세에서 바로 보인다
		mvc.perform(get("/api/portal/events/{id}", eventId).cookie(loginCookie(A)))
			.andExpect(jsonPath("$.status.applicationStatus").value("APPLIED"))
			.andExpect(jsonPath("$.layout.theme").value("DARK"))
			.andExpect(jsonPath("$.logs[0].actor").value("TENANT"));
	}

	@Test
	void activeTenantIsNotLoggedAgain() throws Exception {
		data.tenant(A, "A사", "ACTIVE", "READY");
		long eventId = appliedEventId(apply(A, application(Map.of()), PNG));
		assertThat(data.awaitApplicationStatus(eventId)).isEqualTo("APPLIED");
		assertThat(data.tenantStatusLogs(A)).isEmpty();
		assertThat(data.operatorNotifications(eventId)).hasSize(1);
		assertThat(data.tenantNotifications(A, eventId)).hasSize(1);
	}

	@Test
	void uploadFailureMarksApplyFailedAndKeepsTenantInactive() throws Exception {
		data.tenant(A, "A사", "INACTIVE", "READY");
		doThrow(new RuntimeException("S3 down")).when(banners).put(anyString(), any(), any());

		long eventId = appliedEventId(apply(A, application(Map.of()), PNG));

		assertThat(data.awaitApplicationStatus(eventId)).isEqualTo("APPLY_FAILED");
		assertThat(data.eventRow(A, eventId)).containsEntry("application_status", "APPLY_FAILED")
			.containsEntry("banner_image_path", null);
		assertThat(data.statusLogs(A, eventId)).last()
			.satisfies(l -> assertThat(l).containsEntry("new_status", "APPLY_FAILED").containsEntry("detail", "배너 업로드 실패"));
		assertThat(data.tenantStatusLogs(A)).isEmpty();
		assertThat(data.count("SELECT COUNT(*) FROM tetra_platform.tenant WHERE tenant_id = ? AND account_status = 'INACTIVE'", A)).isEqualTo(1);

		assertThat(data.operatorNotifications(eventId)).singleElement()
			.satisfies(n -> assertThat(n).containsEntry("tenant_id", A).containsEntry("event_id", eventId)
				.containsEntry("category", "APPLICATION").containsEntry("priority", "WARNING")
				.containsEntry("content", "[A사] [" + eventId + ":가을 쿠폰] 이벤트 신청 접수에 실패했습니다. 원인: 배너 업로드 실패"));
		assertThat(data.tenantNotifications(A, eventId)).singleElement()
			.satisfies(n -> assertThat(n).containsEntry("category", "APPLICATION").containsEntry("priority", "WARNING")
				.containsEntry("content", "[" + eventId + ":가을 쿠폰] 이벤트 신청이 접수되지 않았습니다. 다시 신청해 주세요. 원인: 배너 업로드 실패"));
	}

	@Test
	void invalidApplicationReturnsAllReasonsAndStoresNothing() throws Exception {
		data.tenant(A, "A사", "ACTIVE", "READY");
		String json = application(Map.of(
				"\"endAt\":\"" + iso(START.plusHours(2)) + "\"", "\"endAt\":\"" + iso(START.minusHours(1)) + "\"",
				"\"hostingStartAt\":\"" + iso(START.minusHours(2)) + "\"", "\"hostingStartAt\":\"" + iso(START.plusMinutes(30)) + "\"",
				"\"trafficPlanCode\":\"M\"", "\"trafficPlanCode\":\"HIDDEN\"",
				"https://shop.example.com/event", "ftp://shop"));

		apply(A, json, PNG).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.fields.endAt").exists())
			// TEST-LEAD 원래: .andExpect(jsonPath("$.fields.hostingStartAt").value("호스팅 시작은 이벤트 시작 100분 전까지여야 합니다."))
			.andExpect(jsonPath("$.fields.hostingStartAt").value("호스팅 시작은 이벤트 시작 35분 전까지여야 합니다.")) // TEST-LEAD
			.andExpect(jsonPath("$.fields.trafficPlanCode").value("선택할 수 없는 요금제입니다."))
			.andExpect(jsonPath("$.fields.endpointUrl").exists());
		assertThat(data.count("SELECT COUNT(*) FROM tetra_platform.event_registry")).isZero();
		verify(banners, never()).put(anyString(), any(), any());
	}

	@Test
	void hostingStartMustLeadEventStartBy100Minutes() throws Exception {
		data.tenant(A, "A사", "ACTIVE", "READY");
		String hostingStart = "\"hostingStartAt\":\"" + iso(START.minusHours(2)) + "\"";
		// TEST-LEAD 원래: apply(A, application(Map.of(hostingStart, "\"hostingStartAt\":\"" + iso(START.minusMinutes(99)) + "\"")), PNG)
		apply(A, application(Map.of(hostingStart, "\"hostingStartAt\":\"" + iso(START.minusMinutes(34)) + "\"")), PNG) // TEST-LEAD
			.andExpect(status().isBadRequest())
			// TEST-LEAD 원래: .andExpect(jsonPath("$.fields.hostingStartAt").value("호스팅 시작은 이벤트 시작 100분 전까지여야 합니다."));
			.andExpect(jsonPath("$.fields.hostingStartAt").value("호스팅 시작은 이벤트 시작 35분 전까지여야 합니다.")); // TEST-LEAD
		// TEST-LEAD 원래: appliedEventId(apply(A, application(Map.of(hostingStart, "\"hostingStartAt\":\"" + iso(START.minusMinutes(100)) + "\"")), PNG));
		appliedEventId(apply(A, application(Map.of(hostingStart, "\"hostingStartAt\":\"" + iso(START.minusMinutes(35)) + "\"")), PNG)); // TEST-LEAD
	}

	@Test
	void blankNameAndEndpointAreRejected() throws Exception {
		data.tenant(A, "A사", "ACTIVE", "READY");
		apply(A, application(Map.of("\"가을 쿠폰\"", "\"   \"", "https://shop.example.com/event", "   ")), PNG)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.name").exists())
			.andExpect(jsonPath("$.fields.endpointUrl").exists());
		apply(A, application(Map.of("https://shop.example.com/event", "ftp://shop.example.com")), PNG)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.endpointUrl").value("http(s)로 시작하는 주소를 입력해 주세요."));
	}

	@Test
	void couponRulesTypeAndPastHostingAreChecked() throws Exception {
		data.tenant(A, "A사", "ACTIVE", "READY");
		String noCoupons = application(Map.of()).replaceAll("\"coupons\":\\[.*?\\]\\]?", "\"coupons\":[]");
		apply(A, noCoupons, PNG).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.coupons").value("쿠폰을 1개 이상 입력해 주세요."));

		apply(A, application(Map.of("\"COUPON\"", "\"TIMESALE\"")), PNG).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.eventType").value("아직 신청할 수 없는 이벤트 유형입니다."));

		LocalDateTime past = LocalDateTime.now().minusDays(1).withNano(0);
		String pastJson = application(Map.of()).replace(iso(START.minusHours(2)), iso(past))
			.replace(iso(START), iso(past.plusHours(1))).replace(iso(START.plusHours(2)), iso(past.plusHours(2)));
		apply(A, pastJson, PNG).andExpect(status().isBadRequest())
			// TEST-LEAD 원래: .andExpect(jsonPath("$.fields.hostingStartAt").value("호스팅 시작은 지금부터 30분 이후여야 합니다."))
			.andExpect(jsonPath("$.fields.hostingStartAt").value("호스팅 시작은 지금부터 10분 이후여야 합니다.")) // TEST-LEAD
			.andExpect(jsonPath("$.fields.startAt").value("이벤트 시작은 지금보다 뒤여야 합니다."));

		String elevenCoupons = application(Map.of()).replace("{\"name\":\"무료 배송\",\"description\":\"3만원 이상\"}",
				String.join(",", java.util.Collections.nCopies(10, "{\"name\":\"c\",\"description\":\"d\"}")));
		apply(A, elevenCoupons, PNG).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.coupons").value("쿠폰은 10개까지입니다."));
	}

	@Test
	void bannerMustBeSmallPngOrJpeg() throws Exception {
		data.tenant(A, "A사", "ACTIVE", "READY");
		apply(A, application(Map.of()), null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.banner").value("배너 이미지를 올려 주세요."));
		apply(A, application(Map.of()), "GIF89a not allowed".getBytes()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.banner").value("PNG 또는 JPEG 파일만 올릴 수 있습니다."));
		apply(A, application(Map.of()), pngBytes((int) BannerImage.MAX_BYTES + 1)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.banner").value("2MB 이하 파일만 올릴 수 있습니다."));
		byte[] jpeg = Arrays.copyOf(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0 }, 2048);
		long eventId = appliedEventId(apply(A, application(Map.of()), jpeg));
		assertThat(data.awaitApplicationStatus(eventId)).isEqualTo("APPLIED");
		assertThat(data.eventRow(A, eventId)).containsEntry("banner_image_path", "assets/banners/" + eventId + "/banner.jpg");
	}

	@Test
	void layoutAndRequestShapeAreValidated() throws Exception {
		data.tenant(A, "A사", "ACTIVE", "READY");
		apply(A, application(Map.of("\"theme\":\"DARK\"", "\"theme\":\"NEON\"")), PNG).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
		apply(A, application(Map.of("\"title\":{\"h\":\"CENTER\",\"v\":\"TOP\"},", "")), PNG)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields['layout.title']").exists());
		// multipart가 아니면 받지 않는다
		mvc.perform(post("/api/portal/events").cookie(loginCookie(A)).contentType(MediaType.APPLICATION_JSON)
			.content(application(Map.of()))).andExpect(status().isUnsupportedMediaType());
		// 로그인 필요
		mvc.perform(multipart("/api/portal/events")
			.file(new MockMultipartFile("application", "", MediaType.APPLICATION_JSON_VALUE, application(Map.of()).getBytes())))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void sweeperFailsOnlyStaleApplyingOnce() throws Exception {
		data.tenant(A, "A사", "INACTIVE", "READY");
		data.tenant(B, "B사", "INACTIVE", "READY");
		data.event(1001, A).status("APPLYING").appliedAt(LocalDateTime.now().minusMinutes(30).withNano(0)).insert();
		data.event(2001, B).status("APPLYING").appliedAt(LocalDateTime.now().minusMinutes(11).withNano(0)).insert();
		data.event(1002, A).status("APPLYING").appliedAt(LocalDateTime.now().minusMinutes(2).withNano(0)).insert();

		assertThat(sweeper.sweep()).isEqualTo(2);
		assertThat(data.registry(1001)).containsEntry("application_status", "APPLY_FAILED");
		assertThat(data.eventRow(A, 1001)).containsEntry("application_status", "APPLY_FAILED");
		assertThat(data.eventRow(B, 2001)).containsEntry("application_status", "APPLY_FAILED");
		assertThat(data.statusLogs(A, 1001)).last().satisfies(l -> assertThat(l).containsEntry("detail", "배너 업로드 시간 초과"));
		assertThat(data.registry(1002)).containsEntry("application_status", "APPLYING"); // 아직 10분 안
		assertThat(data.operatorNotifications(1001)).singleElement()
			.satisfies(n -> assertThat(n).containsEntry("tenant_id", A).containsEntry("priority", "WARNING")
				.containsEntry("content", "[A사] [1001:이벤트 1001] 이벤트 신청 접수에 실패했습니다. 원인: 배너 업로드 시간 초과"));
		assertThat(data.tenantNotifications(A, 1001)).singleElement()
			.satisfies(n -> assertThat(n).containsEntry("priority", "WARNING")
				.containsEntry("content", "[1001:이벤트 1001] 이벤트 신청이 접수되지 않았습니다. 다시 신청해 주세요. 원인: 배너 업로드 시간 초과"));
		assertThat(data.operatorNotifications(2001)).singleElement()
			.satisfies(n -> assertThat(n).containsEntry("tenant_id", B).containsEntry("priority", "WARNING"));
		assertThat(data.tenantNotifications(B, 2001)).hasSize(1);
		assertThat(data.operatorNotifications(1002)).isEmpty();
		assertThat(data.tenantNotifications(A, 1002)).isEmpty();

		assertThat(sweeper.sweep()).isZero(); // 이미 처리된 것은 다시 기록하지 않는다
		assertThat(data.statusLogs(A, 1001)).hasSize(1);
		assertThat(data.tenantStatusLogs(A)).isEmpty();
		assertThat(data.operatorNotifications(1001)).hasSize(1);
		assertThat(data.tenantNotifications(A, 1001)).hasSize(1);
	}

	@Test
	void appliedAtUsesServerTimeNotRequest() throws Exception {
		data.tenant(A, "A사", "ACTIVE", "READY");
		LocalDateTime before = LocalDateTime.now().withNano(0).minusSeconds(1);
		long eventId = appliedEventId(apply(A, application(Map.of()), PNG));
		assertThat((LocalDateTime) data.registry(eventId).get("applied_at")).isAfterOrEqualTo(before);
		assertThat(data.awaitApplicationStatus(eventId)).isEqualTo("APPLIED"); // 업로드가 다음 시험으로 넘어가지 않게
	}

}
