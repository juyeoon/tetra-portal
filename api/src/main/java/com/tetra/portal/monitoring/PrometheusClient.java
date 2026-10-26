package com.tetra.portal.monitoring;

import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.tetra.portal.config.TetraProperties;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Prometheus HTTP API(/api/v1/query, /api/v1/query_range) GET 호출. 인증 없음.
 * 실패는 {@link PrometheusException} 하나로 바꾸고, 메시지에 쿼리 원문 · 응답 본문을 넣지 않는다.
 * 값 "NaN" · "+Inf" · "-Inf"는 null로 준다.
 */
@Component
public class PrometheusClient {

	/** instant 쿼리 결과 한 줄(vector). value가 NaN · Inf면 null */
	public record Sample(Map<String, String> labels, Double value) {
	}

	/** range 쿼리 결과 한 줄(matrix). 시각은 epoch 밀리초 */
	public record Series(Map<String, String> labels, List<Point> points) {
	}

	public record Point(long epochMillis, Double value) {
	}

	/**
	 * 응답 실패 · 형식 오류 · 미설정. 메시지는 로그에 그대로 써도 되는 짧은 원인만.
	 * serverFailure: Prometheus 자체 문제(연결 거부 · 연결 시간 초과 · 5xx · 미설정 · 갱신 상한 초과)면 true,
	 * 그 쿼리만의 문제(4xx · 읽기 시간 초과 · 형식 오류)면 false — PromBatch가 그 항목만 비운다.
	 */
	public static class PrometheusException extends RuntimeException {

		private final boolean serverFailure;

		public PrometheusException(String message) {
			this(message, true);
		}

		public PrometheusException(String message, boolean serverFailure) {
			super(message);
			this.serverFailure = serverFailure;
		}

		public boolean serverFailure() {
			return serverFailure;
		}

	}

	private final RestClient restClient;

	private final JsonMapper jsonMapper;

	public PrometheusClient(TetraProperties properties, JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
		TetraProperties.Monitoring monitoring = properties.monitoring();
		this.restClient = monitoring.configured() ? restClient(monitoring) : null;
	}

	private static RestClient restClient(TetraProperties.Monitoring monitoring) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(monitoring.connectTimeout()).build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(monitoring.readTimeout());
		String base = monitoring.prometheusUrl().strip().replaceAll("/+$", "");
		return RestClient.builder().baseUrl(base).requestFactory(factory).build();
	}

	public boolean configured() {
		return restClient != null;
	}

	/** instant 쿼리. time = 평가 시각(한 번의 갱신에서 같은 시각을 쓰려고) */
	public List<Sample> query(String promql, Instant time) {
		JsonNode result = get("/api/v1/query?query={query}&time={time}",
				Map.of("query", promql, "time", epochSeconds(time)), "vector");
		List<Sample> samples = new ArrayList<>();
		for (int i = 0; i < result.size(); i++) {
			JsonNode item = result.get(i);
			samples.add(new Sample(labels(item.path("metric")), value(item.path("value").path(1))));
		}
		return samples;
	}

	/** range 쿼리. 시각이 step 배수면 Prometheus가 start + k·step 시각으로 돌려준다 */
	public List<Series> queryRange(String promql, Instant start, Instant end, Duration step) {
		JsonNode result = get("/api/v1/query_range?query={query}&start={start}&end={end}&step={step}",
				Map.of("query", promql, "start", epochSeconds(start), "end", epochSeconds(end), "step",
						step.toSeconds() + "s"),
				"matrix");
		List<Series> series = new ArrayList<>();
		for (int i = 0; i < result.size(); i++) {
			JsonNode item = result.get(i);
			JsonNode values = item.path("values");
			List<Point> points = new ArrayList<>(values.size());
			for (int j = 0; j < values.size(); j++) {
				JsonNode pair = values.get(j);
				points.add(new Point(Math.round(pair.path(0).asDouble() * 1000), value(pair.path(1))));
			}
			series.add(new Series(labels(item.path("metric")), points));
		}
		return series;
	}

	private JsonNode get(String uri, Map<String, ?> variables, String expectedType) {
		if (restClient == null) {
			throw new PrometheusException("PROMETHEUS_URL 미설정");
		}
		String body;
		try {
			body = restClient.get().uri(uri, variables).retrieve().body(String.class);
		}
		catch (RestClientResponseException ex) {
			// 응답 본문(오류 문구에 쿼리가 들어 있음)은 쓰지 않는다. 4xx(잘못된 쿼리 · 실행 오류)는 그 쿼리만의 문제
			throw new PrometheusException("HTTP " + ex.getStatusCode().value(), ex.getStatusCode().is5xxServerError());
		}
		catch (RestClientException ex) {
			Throwable cause = ex.getMostSpecificCause();
			// 읽기 시간 초과는 그 쿼리만(무거운 쿼리), 연결 실패 · 연결 시간 초과는 서버 문제
			boolean readTimeout = cause instanceof HttpTimeoutException && !(cause instanceof HttpConnectTimeoutException);
			throw new PrometheusException(cause.getClass().getSimpleName(), !readTimeout);
		}
		JsonNode root;
		try {
			root = jsonMapper.readTree(body == null ? "" : body);
		}
		catch (JacksonException ex) {
			throw new PrometheusException("응답 형식 오류", false);
		}
		JsonNode data = root.path("data");
		if (!"success".equals(root.path("status").asString(null))
				|| !expectedType.equals(data.path("resultType").asString(null)) || !data.path("result").isArray()) {
			throw new PrometheusException("응답 형식 오류", false);
		}
		return data.path("result");
	}

	private static Map<String, String> labels(JsonNode metric) {
		Map<String, String> labels = new LinkedHashMap<>();
		metric.properties().forEach(e -> labels.put(e.getKey(), e.getValue().asString("")));
		return labels;
	}

	/** Prometheus 값은 문자열("706.1", "NaN", "+Inf", "-Inf") */
	static Double value(JsonNode node) {
		if (!node.isString()) {
			return null;
		}
		double parsed;
		try {
			parsed = Double.parseDouble(node.asString());
		}
		catch (NumberFormatException ex) {
			return null; // "+Inf" · "-Inf"는 Java 표기(Infinity)가 아니라 여기로 온다
		}
		return Double.isNaN(parsed) || Double.isInfinite(parsed) ? null : parsed;
	}

	private static String epochSeconds(Instant time) {
		return time.getEpochSecond() + (time.getNano() == 0 ? "" : String.format(".%03d", time.getNano() / 1_000_000));
	}

}
