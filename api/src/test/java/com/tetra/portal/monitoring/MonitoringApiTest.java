package com.tetra.portal.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.tetra.portal.support.FakePrometheus;
import com.tetra.portal.support.FakePrometheus.Request;
import com.tetra.portal.support.IntegrationTest;

/**
 * UI-TP-11 모니터링 API — 실제 MySQL(테넌트 스키마 2개) + 가짜 Prometheus(JDK HttpServer).
 * 소유 확인(다른 테넌트 → EVENT_NOT_FOUND), 모든 쿼리의 tenant_id · event_id, 상태별 조회 여부, 데이터 지연.
 * 캐시 TTL 0 — 요청마다 Prometheus를 다시 읽는다(캐시 · 시각 경계는 MonitoringServiceTest).
 */
class MonitoringApiTest extends IntegrationTest {

	static final FakePrometheus prometheus = new FakePrometheus();

	@DynamicPropertySource
	static void monitoring(DynamicPropertyRegistry registry) {
		registry.add("tetra.monitoring.prometheus-url", prometheus::url);
		registry.add("tetra.monitoring.cache-ttl", () -> "PT0S");
	}

	@AfterAll
	static void stop() {
		prometheus.close();
	}

	/** 지금 기준 시각. DATETIME은 초 단위 */
	static final LocalDateTime NOW = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

	@BeforeEach
	void reset() {
		prometheus.reset();
		data.tenant(A, "A사", "ACTIVE", "READY");
		data.tenant(B, "B사", "ACTIVE", "READY");
	}

	/** 진행 중(시작 10분 전 → 지금은 시작 뒤 10분) */
	void liveEvent(long id, String tenant) {
		data.event(id, tenant).approved("HOSTING", "OPEN", "live0" + id).startAt(NOW.minusMinutes(10)).insert();
	}

	/** 정상 진행 중 값. 재고 10000 중 4540 남음 */
	void respondLive(long eventId, boolean newMetrics) {
		var q = new MonitoringQueries(A, eventId);
		prometheus.respond(q.stockRemaining(), r -> r.path().endsWith("query_range") ? matrix(r, "4540") : vector("4540"))
			.respond(q.waiting(), vector("1200"))
			.respond(q.redisUp(), vector("1"))
			.respond(q.processedLastMinute(), vector("600"))
			.respond(q.successRate5m(), vector("9.5"))
			.respond(q.rps(), vector("120"))
			.respond(q.errorRps(), vector("0"))
			.respond(q.claimP95(), vector("0.12"))
			.respond(q.entriesPerMinute(), r -> matrix(r, "40"))
			.respond(q.claimsPerMinute(), r -> "[" + matrix(r, "500", "success") + "," + matrix(r, "3", "sold_out") + "]");
		prometheus.respondStartingWith(q.soldOutSince(Duration.ZERO).replace("[60s]))", ""), vector("12"));
		if (newMetrics) {
			prometheus.respond(q.sessionsActive(), r -> r.path().endsWith("query_range") ? matrix(r, "6100") : vector("6820"))
				.respond(q.rejects5m(), vector("2"));
		}
	}

	static String vector(String value) {
		return "[{\"metric\":{},\"value\":[1,\"" + value + "\"]}]";
	}

	/** 요청한 격자(start ~ end, step) 모든 시각에 같은 값. result 라벨을 주면 그 시리즈 하나만(JSON 배열 원소) */
	static String matrix(Request r, String value, String... result) {
		long start = (long) Double.parseDouble(r.params().get("start"));
		long end = (long) Double.parseDouble(r.params().get("end"));
		List<String> points = new ArrayList<>();
		for (long t = start; t <= end; t += 60) {
			points.add("[" + t + ",\"" + value + "\"]");
		}
		String labels = result.length == 0 ? "{}" : "{\"result\":\"" + result[0] + "\"}";
		String series = "{\"metric\":" + labels + ",\"values\":[" + String.join(",", points) + "]}";
		return result.length == 0 ? "[" + series + "]" : series;
	}

	static final Pattern METRIC = Pattern
		.compile("(tetra_[a-z_]+|http_server_requests_seconds_(?:count|bucket)|redis_up)\\{([^\\n]*)");

	// ---- 목록 ----

	@Test
	void 목록은_로그인한_테넌트의_승인된_이벤트만_Prometheus는_읽지_않는다() throws Exception {
		liveEvent(501, A);
		data.event(502, A).startAt(NOW.minusMinutes(10)).insert(); // 신청만(APPLIED)
		data.event(503, A).rejected("반려").insert();
		liveEvent(504, B);

		mvc.perform(get("/api/portal/monitoring/events").cookie(loginCookie(A)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.events.length()").value(1))
			.andExpect(jsonPath("$.events[0].eventId").value(501))
			.andExpect(jsonPath("$.events[0].group").value("LIVE"))
			.andExpect(jsonPath("$.events[0].state").value("LIVE"))
			.andExpect(jsonPath("$.defaultEventId").value(501));
		assertThat(prometheus.requests()).isEmpty();
	}

	@Test
	void 볼_이벤트가_없으면_빈_목록() throws Exception {
		mvc.perform(get("/api/portal/monitoring/events").cookie(loginCookie(A)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.events.length()").value(0))
			.andExpect(jsonPath("$.defaultEventId").doesNotExist());
	}

	// ---- 소유 확인 ----

	@Test
	void 다른_테넌트의_이벤트는_EVENT_NOT_FOUND() throws Exception {
		liveEvent(504, B);
		mvc.perform(get("/api/portal/monitoring/events/{id}", 504).cookie(loginCookie(A)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
		assertThat(prometheus.requests()).isEmpty();
	}

	@Test
	void 승인되지_않은_이벤트는_EVENT_NOT_FOUND() throws Exception {
		data.event(502, A).startAt(NOW.minusMinutes(10)).insert();
		mvc.perform(get("/api/portal/monitoring/events/{id}", 502).cookie(loginCookie(A)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
	}

	@Test
	void 로그인하지_않으면_401() throws Exception {
		mvc.perform(get("/api/portal/monitoring/events")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/portal/monitoring/events/{id}", 501)).andExpect(status().isUnauthorized());
	}

	// ---- 진행 중 ----

	@Test
	void 진행_중_전체_응답과_모든_쿼리의_tenant_id_event_id() throws Exception {
		liveEvent(501, A);
		respondLive(501, true);

		mvc.perform(get("/api/portal/monitoring/events/{id}", 501).cookie(loginCookie(A)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("LIVE"))
			.andExpect(jsonPath("$.stale").value(false))
			.andExpect(jsonPath("$.lastUpdatedAt").exists())
			.andExpect(jsonPath("$.period.domain").value("live0501." + A + "." + A + ".example.com"))
			.andExpect(jsonPath("$.steps[2].step").value("LIVE"))
			.andExpect(jsonPath("$.steps[2].status").value("CURRENT"))
			.andExpect(jsonPath("$.header.service").value("NORMAL"))
			.andExpect(jsonPath("$.header.soldOutAt").doesNotExist())
			.andExpect(jsonPath("$.kpi.issued").value(10000 - 4540))
			.andExpect(jsonPath("$.kpi.stock").value(10000))
			.andExpect(jsonPath("$.kpi.soldOutCount").value(12))
			.andExpect(jsonPath("$.kpi.waiting").value(1200))
			.andExpect(jsonPath("$.kpi.waitMinutes").value(2))
			.andExpect(jsonPath("$.kpi.activeSessions").value(6820))
			.andExpect(jsonPath("$.kpi.planCapacity").value(30000))
			.andExpect(jsonPath("$.kpi.planName").value("M"))
			.andExpect(jsonPath("$.kpi.peak.value").value(6820))
			.andExpect(jsonPath("$.series.perMinute[0].entries").value(40))
			.andExpect(jsonPath("$.series.perMinute[0].success").value(500))
			.andExpect(jsonPath("$.series.perMinute[0].soldOut").value(3))
			.andExpect(jsonPath("$.series.stock[0].remaining").value(4540))
			.andExpect(jsonPath("$.series.projection.at").exists())
			.andExpect(jsonPath("$.rejects5m").value(2));

		// instant 11 + range 4, 전부 이 테넌트 · 이 이벤트 라벨
		assertThat(prometheus.requests()).hasSize(15).allSatisfy(r -> {
			Matcher m = METRIC.matcher(r.query());
			int metrics = 0;
			while (m.find()) {
				metrics++;
				assertThat(m.group(2)).as(r.query()).startsWith("tenant_id=\"" + A + "\",event_id=\"501\"");
			}
			assertThat(metrics).as(r.query()).isPositive();
		});
	}

	@Test
	void 새_지표가_아직_없으면_그_칸만_null() throws Exception {
		liveEvent(501, A);
		respondLive(501, false);

		mvc.perform(get("/api/portal/monitoring/events/{id}", 501).cookie(loginCookie(A)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.kpi.activeSessions").doesNotExist())
			.andExpect(jsonPath("$.kpi.peak").doesNotExist())
			.andExpect(jsonPath("$.rejects5m").doesNotExist())
			.andExpect(jsonPath("$.kpi.issued").value(10000 - 4540))
			.andExpect(jsonPath("$.header.service").value("NORMAL"));
	}

	// ---- 상태별 조회 ----

	@Test
	void 종료와_입장_전은_Prometheus를_읽지_않고_kpi_series가_없다() throws Exception {
		data.event(601, A).approved("ENDED", "EXPIRED", "endd0601").startAt(NOW.minusHours(6)).insert();
		data.event(602, A).approved("DEPLOYED", "SCHEDULED", "prep0602").startAt(NOW.plusHours(3)).insert();

		mvc.perform(get("/api/portal/monitoring/events/{id}", 601).cookie(loginCookie(A)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("ENDED"))
			.andExpect(jsonPath("$.kpi").doesNotExist())
			.andExpect(jsonPath("$.series").doesNotExist())
			.andExpect(jsonPath("$.header.endsInSec").doesNotExist())
			.andExpect(jsonPath("$.steps[4].step").value("AGGREGATE"))
			.andExpect(jsonPath("$.steps[4].status").value("CURRENT"));
		mvc.perform(get("/api/portal/monitoring/events/{id}", 602).cookie(loginCookie(A)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("BEFORE_ENTRY"))
			// 호스팅 시작(시작 − 2시간) 전 — 완료되지 않은 첫 단계(호스팅 시작)가 현재
			.andExpect(jsonPath("$.steps[0].status").value("CURRENT"))
			.andExpect(jsonPath("$.steps[1].status").value("UPCOMING"))
			.andExpect(jsonPath("$.kpi").doesNotExist())
			.andExpect(jsonPath("$.header.service").doesNotExist());
		assertThat(prometheus.requests()).isEmpty();
	}

	@Test
	void 호스팅_시작_칸은_시각이_아니라_실제_상태와_첫_전이_로그로() throws Exception {
		// 셋 다 호스팅 시작(시작 − 2시간 = 30분 전)이 지났고 입장 전
		data.event(603, A).approved("HOSTING", "SCHEDULED", "host0603").startAt(NOW.plusMinutes(90)).insert();
		data.statusLog(A, 603, "HOSTING", "DEPLOYED", "HOSTING", NOW.minusMinutes(29), null, null);
		data.event(604, A).approved("DEPLOYED", "SCHEDULED", "wait0604").startAt(NOW.plusMinutes(90)).insert();
		data.event(605, A).approved("HOSTING_FAILED", "SCHEDULED", "fail0605").startAt(NOW.plusMinutes(90)).insert();
		data.statusLog(A, 605, "HOSTING", "DEPLOYED", "HOSTING_FAILED", NOW.minusMinutes(27), "페이지 확인 시간 초과", null);

		mvc.perform(get("/api/portal/monitoring/events/{id}", 603).cookie(loginCookie(A)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.steps[0].status").value("DONE"))
			.andExpect(jsonPath("$.steps[0].actualAt").value(startsWith(NOW.minusMinutes(29).toString().substring(0, 16))))
			.andExpect(jsonPath("$.steps[0].delayed").value(false))
			.andExpect(jsonPath("$.steps[1].status").value("CURRENT"))
			.andExpect(jsonPath("$.steps[1].actualAt").doesNotExist());
		mvc.perform(get("/api/portal/monitoring/events/{id}", 604).cookie(loginCookie(A)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.steps[0].status").value("CURRENT"))
			.andExpect(jsonPath("$.steps[0].delayed").value(true))
			.andExpect(jsonPath("$.steps[1].status").value("UPCOMING"));
		mvc.perform(get("/api/portal/monitoring/events/{id}", 605).cookie(loginCookie(A)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("BEFORE_ENTRY")) // 상태는 시각 기준 그대로
			.andExpect(jsonPath("$.steps[0].status").value("FAILED"))
			.andExpect(jsonPath("$.steps[0].delayed").value(false))
			.andExpect(jsonPath("$.steps[1].status").value("UPCOMING"));
	}

	// ---- 데이터 지연 ----

	@Test
	void Prometheus가_내려가면_마지막_값을_stale로_처음부터_실패면_수치_null() throws Exception {
		liveEvent(501, A);
		liveEvent(505, A);
		respondLive(501, true);
		mvc.perform(get("/api/portal/monitoring/events/{id}", 501).cookie(loginCookie(A)))
			.andExpect(jsonPath("$.stale").value(false));

		prometheus.failWith(503);
		mvc.perform(get("/api/portal/monitoring/events/{id}", 501).cookie(loginCookie(A)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.stale").value(true))
			.andExpect(jsonPath("$.lastUpdatedAt").exists())
			.andExpect(jsonPath("$.header.service").value("CHECKING"))
			.andExpect(jsonPath("$.kpi.waiting").value(1200)); // 마지막 정상 값

		mvc.perform(get("/api/portal/monitoring/events/{id}", 505).cookie(loginCookie(A)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("LIVE"))
			.andExpect(jsonPath("$.stale").value(true))
			.andExpect(jsonPath("$.lastUpdatedAt").doesNotExist())
			.andExpect(jsonPath("$.kpi.stock").value(10000))
			.andExpect(jsonPath("$.kpi.issued").doesNotExist())
			.andExpect(jsonPath("$.series.perMinute.length()").value(0));
	}

	@Test
	void 쿼리_하나가_틀리면_그_값만_null() throws Exception {
		liveEvent(501, A);
		respondLive(501, true);
		prometheus.failQuery(new MonitoringQueries(A, 501).waiting(), 400);

		mvc.perform(get("/api/portal/monitoring/events/{id}", 501).cookie(loginCookie(A)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.stale").value(false))
			.andExpect(jsonPath("$.kpi.waiting").doesNotExist())
			.andExpect(jsonPath("$.kpi.waitMinutes").doesNotExist())
			.andExpect(jsonPath("$.kpi.issued").value(10000 - 4540));
	}

}
