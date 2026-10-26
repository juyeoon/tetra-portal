package com.tetra.portal.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tetra.portal.config.TetraProperties;
import com.tetra.portal.monitoring.PrometheusClient.PrometheusException;
import com.tetra.portal.support.FakePrometheus;

import tools.jackson.databind.json.JsonMapper;

/** Console PrometheusClientTest 이식. Prometheus 응답 파싱(vector · matrix · 빈 결과 · NaN/Inf) · 인코딩 · 실패 · 타임아웃. */
class PrometheusClientTest {

	private static final FakePrometheus fake = new FakePrometheus();

	private static final Instant AT = Instant.parse("2026-10-26T06:00:00Z");

	/** 인코딩 확인용 — {} " = ~ | + 가 들어 있는 식 */
	private static final String RPS = "sum by (tenant_id) (rate(http_server_requests_seconds_count{uri!~\"/actuator.*\",status=~\"5..|2..\"}[1m])) + 0";

	private static final String CPU = "1 - avg(rate(node_cpu_seconds_total{mode=\"idle\"}[5m]))";

	@AfterAll
	static void stop() {
		fake.close();
	}

	@BeforeEach
	void reset() {
		fake.reset();
	}

	static PrometheusClient client(String url, Duration readTimeout) {
		var monitoring = new TetraProperties.Monitoring(url, Duration.ofSeconds(2), readTimeout, Duration.ofSeconds(15),
				Duration.ofSeconds(8));
		var properties = new TetraProperties("test", null, null, null, null, null, null, monitoring, "http://localhost:5174");
		return new PrometheusClient(properties, JsonMapper.builder().build());
	}

	private final PrometheusClient client = client(fake.url() + "/", Duration.ofSeconds(3));

	@Test
	void parsesVectorWithLabelsAndPassesQueryAndTimeUnchanged() {
		fake.respond(RPS, """
				[{"metric":{"tenant_id":"aaaaaaaaaaaa"},"value":[1792972800,"706.1"]},
				 {"metric":{"tenant_id":"bbbbbbbbbbbb"},"value":[1792972800,"0"]}]""");

		var samples = client.query(RPS, AT);

		assertThat(samples).extracting(PrometheusClient.Sample::labels)
			.containsExactly(Map.of("tenant_id", "aaaaaaaaaaaa"), Map.of("tenant_id", "bbbbbbbbbbbb"));
		assertThat(samples).extracting(PrometheusClient.Sample::value).containsExactly(706.1, 0.0);
		// {} " = ~ | + 등이 인코딩돼 그대로 도착(끝 / 는 떼고 경로를 붙임)
		assertThat(fake.requests()).singleElement().satisfies(r -> {
			assertThat(r.path()).isEqualTo("/api/v1/query");
			assertThat(r.query()).isEqualTo(RPS);
			assertThat(r.params()).containsEntry("time", String.valueOf(AT.getEpochSecond()));
		});
	}

	@Test
	void nanAndInfinityBecomeNullAndEmptyResultIsEmpty() {
		fake.respond("a", """
				[{"metric":{},"value":[1,"NaN"]},{"metric":{},"value":[1,"+Inf"]},{"metric":{},"value":[1,"-Inf"]},
				 {"metric":{},"value":[1,"0.42"]}]""");

		assertThat(client.query("a", AT)).extracting(PrometheusClient.Sample::value).containsExactly(null, null, null, 0.42);
		assertThat(client.query("empty", AT)).isEmpty();
	}

	@Test
	void parsesMatrixAndSendsRangeParams() {
		fake.respond(RPS, """
				[{"metric":{"tenant_id":"aaaaaaaaaaaa"},"values":[[1792972800,"1.5"],[1792972815.5,"NaN"]]}]""");
		Instant start = AT.minus(Duration.ofMinutes(30));

		var series = client.queryRange(RPS, start, AT, Duration.ofSeconds(15));

		assertThat(series).singleElement().satisfies(s -> {
			assertThat(s.labels()).containsEntry("tenant_id", "aaaaaaaaaaaa");
			assertThat(s.points()).containsExactly(new PrometheusClient.Point(1792972800000L, 1.5),
					new PrometheusClient.Point(1792972815500L, null));
		});
		assertThat(fake.requests()).singleElement().satisfies(r -> {
			assertThat(r.path()).isEqualTo("/api/v1/query_range");
			assertThat(r.params()).containsEntry("start", String.valueOf(start.getEpochSecond()))
				.containsEntry("end", String.valueOf(AT.getEpochSecond()))
				.containsEntry("step", "15s");
		});
		assertThat(client.queryRange("empty", start, AT, Duration.ofSeconds(15))).isEmpty();
	}

	@Test
	void httpErrorDoesNotExposeBody() {
		fake.failWith(400);

		assertThatThrownBy(() -> client.query(CPU, AT)).isInstanceOf(PrometheusException.class)
			.hasMessage("HTTP 400");
	}

	@Test
	void wrongResultTypeIsFormatError() {
		fake.respond("x", "{}"); // result가 배열이 아님

		assertThatThrownBy(() -> client.query("x", AT)).isInstanceOf(PrometheusException.class).hasMessage("응답 형식 오류");
	}

	@Test
	void readTimeout() {
		fake.delay(1500);
		PrometheusClient slow = client(fake.url(), Duration.ofMillis(300));

		assertThatThrownBy(() -> slow.query(CPU, AT)).isInstanceOf(PrometheusException.class)
			.message()
			.doesNotContain("node_cpu");
	}

	@Test
	void connectionRefused() {
		PrometheusClient down = client("http://127.0.0.1:1", Duration.ofSeconds(1));

		assertThatThrownBy(() -> down.query(CPU, AT)).isInstanceOf(PrometheusException.class);
	}

	@Test
	void unconfiguredClient() {
		PrometheusClient unconfigured = client("  ", Duration.ofSeconds(3));

		assertThat(unconfigured.configured()).isFalse();
		assertThatThrownBy(() -> unconfigured.query(CPU, AT)).isInstanceOf(PrometheusException.class)
			.hasMessage("PROMETHEUS_URL 미설정");
	}

}
