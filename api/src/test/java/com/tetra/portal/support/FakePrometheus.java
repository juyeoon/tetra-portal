package com.tetra.portal.support;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Prometheus HTTP API 목(JDK 내장 HttpServer — 라이브러리 추가 없음). 실제 HTTP라 인코딩 · 타임아웃까지 확인한다.
 * 쿼리 원문(디코딩한 query 파라미터)별로 응답을 정하고, 정하지 않은 쿼리는 빈 결과를 준다.
 */
public final class FakePrometheus implements AutoCloseable {

	/** 요청 하나. params는 디코딩한 쿼리 파라미터 */
	public record Request(String path, Map<String, String> params) {

		public String query() {
			return params.get("query");
		}

	}

	private final HttpServer server;

	/** 요청을 동시에 받는다(OPS-05 병렬 조회 확인) */
	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	private final Map<String, Function<Request, String>> responses = new ConcurrentHashMap<>();

	private final Map<String, String> prefixResponses = new ConcurrentHashMap<>();

	private final List<Request> requests = new CopyOnWriteArrayList<>();

	private volatile int failStatus;

	private volatile long delayMillis;

	private final Map<String, Integer> queryFailures = new ConcurrentHashMap<>();

	private final Map<String, Long> queryDelays = new ConcurrentHashMap<>();

	public FakePrometheus() {
		try {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
		server.createContext("/", this::handle);
		server.setExecutor(executor);
		server.start();
	}

	public String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	/** 이 쿼리에 줄 data.result(JSON 배열) */
	public FakePrometheus respond(String query, String resultJson) {
		return respond(query, request -> resultJson);
	}

	public FakePrometheus respond(String query, Function<Request, String> resultJson) {
		responses.put(query, resultJson);
		return this;
	}

	/** 이 접두어로 시작하는 쿼리(정확히 맞는 응답이 없을 때). 범위가 시각에 따라 바뀌는 쿼리용 */
	public FakePrometheus respondStartingWith(String prefix, String resultJson) {
		prefixResponses.put(prefix, resultJson);
		return this;
	}

	/** 0이 아니면 모든 요청에 이 상태 코드(본문에 쿼리를 넣어 응답 본문이 새지 않는지 확인) */
	public void failWith(int status) {
		this.failStatus = status;
	}

	public void delay(long millis) {
		this.delayMillis = millis;
	}

	/** 이 쿼리만 이 상태 코드(OPS-05 쿼리 하나의 오류) */
	public FakePrometheus failQuery(String query, int status) {
		queryFailures.put(query, status);
		return this;
	}

	/** 이 쿼리만 늦게 응답(읽기 시간 초과) */
	public FakePrometheus delayQuery(String query, long millis) {
		queryDelays.put(query, millis);
		return this;
	}

	public List<Request> requests() {
		return requests;
	}

	public void reset() {
		responses.clear();
		prefixResponses.clear();
		requests.clear();
		failStatus = 0;
		delayMillis = 0;
		queryFailures.clear();
		queryDelays.clear();
	}

	private void handle(HttpExchange exchange) throws IOException {
		Request request = new Request(exchange.getRequestURI().getPath(), params(exchange.getRequestURI().getRawQuery()));
		requests.add(request);
		long delay = delayMillis + queryDelays.getOrDefault(String.valueOf(request.query()), 0L);
		if (delay > 0) {
			try {
				Thread.sleep(delay);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}
		int status = 200;
		String body;
		int fail = failStatus != 0 ? failStatus : queryFailures.getOrDefault(String.valueOf(request.query()), 0);
		if (fail != 0) {
			status = fail;
			body = "{\"status\":\"error\",\"error\":\"bad query " + request.query() + "\"}";
		}
		else {
			String resultType = request.path().endsWith("query_range") ? "matrix" : "vector";
			String query = String.valueOf(request.query());
			Function<Request, String> response = responses.get(query);
			String result = response != null ? response.apply(request)
					: prefixResponses.entrySet()
						.stream()
						.filter(e -> query.startsWith(e.getKey()))
						.map(Map.Entry::getValue)
						.findFirst()
						.orElse("[]");
			body = "{\"status\":\"success\",\"data\":{\"resultType\":\"" + resultType + "\",\"result\":" + result + "}}";
		}
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		try {
			exchange.sendResponseHeaders(status, bytes.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		}
		catch (IOException ex) {
			// 클라이언트가 타임아웃으로 먼저 끊은 경우
		}
	}

	private static Map<String, String> params(String rawQuery) {
		Map<String, String> params = new ConcurrentHashMap<>();
		if (rawQuery == null) {
			return params;
		}
		for (String pair : rawQuery.split("&")) {
			int eq = pair.indexOf('=');
			String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
			String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
			params.put(key, value);
		}
		return params;
	}

	@Override
	public void close() {
		server.stop(0);
		executor.shutdownNow();
	}

}
