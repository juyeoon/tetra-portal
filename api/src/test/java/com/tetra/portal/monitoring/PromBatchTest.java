package com.tetra.portal.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.tetra.portal.monitoring.PrometheusClient.PrometheusException;
import com.tetra.portal.support.FakePrometheus;

/**
 * Console PromBatchTest 이식. 쿼리 묶음: 4개씩 병렬. Prometheus 자체 문제(연결 거부 · 5xx · 갱신 상한 초과)면 전체 실패,
 * 쿼리 하나의 오류(4xx · 읽기 시간 초과)면 그 항목만 비우고 WARN 한 줄(쿼리 이름만)
 */
@ExtendWith(OutputCaptureExtension.class)
class PromBatchTest {

	private static final FakePrometheus fake = new FakePrometheus();

	private final PrometheusClient client = PrometheusClientTest.client(fake.url(), Duration.ofSeconds(3));

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	@AfterAll
	static void stop() {
		fake.close();
	}

	@BeforeEach
	void reset() {
		fake.reset();
	}

	@AfterEach
	void shutdown() {
		executor.shutdownNow();
	}

	private PromBatch batch(Duration timeout) {
		return new PromBatch(client, executor, System.nanoTime() + timeout.toNanos());
	}

	@Test
	void runsInParallelAndReturnsByName() {
		fake.respond("q1", "[{\"metric\":{},\"value\":[1,\"1\"]}]").delay(300);
		PromBatch batch = batch(Duration.ofSeconds(5));
		for (int i = 0; i < 8; i++) {
			batch.instant("n" + i, "q" + (i % 2 == 0 ? 1 : 2), Instant.now());
		}
		long started = System.nanoTime();
		PromBatch.Results results = batch.execute();
		// 8개 × 300ms를 4개씩 → 약 600ms(순차면 2.4초)
		assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(1500));
		assertThat(results.instant("n0").get(0).value()).isEqualTo(1.0);
		assertThat(results.instant("n1")).isEmpty();
		assertThatThrownBy(() -> results.instant("missing")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void serverErrorFailsTheBatch() {
		fake.failWith(503);
		PromBatch batch = batch(Duration.ofSeconds(5)).instant("a", "q1", Instant.now()).instant("b", "q2", Instant.now());
		assertThatThrownBy(batch::execute).isInstanceOf(PrometheusException.class).hasMessage("HTTP 503");
	}

	@Test
	void oneQuery5xxFailsTheBatch() {
		fake.respond("q1", "[{\"metric\":{},\"value\":[1,\"1\"]}]").failQuery("q2", 500);
		PromBatch batch = batch(Duration.ofSeconds(5)).instant("a", "q1", Instant.now()).instant("b", "q2", Instant.now());
		assertThatThrownBy(batch::execute).isInstanceOf(PrometheusException.class).hasMessage("HTTP 500");
	}

	@Test
	void connectionRefusedFailsTheBatch() {
		FakePrometheus closed = new FakePrometheus();
		String url = closed.url();
		closed.close();
		PrometheusClient down = PrometheusClientTest.client(url, Duration.ofSeconds(3));
		PromBatch batch = new PromBatch(down, executor, System.nanoTime() + Duration.ofSeconds(5).toNanos())
			.instant("a", "q1", Instant.now());
		assertThatThrownBy(batch::execute).isInstanceOf(PrometheusException.class)
			.satisfies(ex -> assertThat(((PrometheusException) ex).serverFailure()).isTrue());
	}

	@Test
	void queryErrorBlanksOnlyThatItem(CapturedOutput output) {
		String bad = "sum(rate(bad_query_text{secret=\"x\"}[1m]))";
		fake.respond("q1", "[{\"metric\":{},\"value\":[1,\"1\"]}]").failQuery(bad, 400);
		PromBatch.Results results = batch(Duration.ofSeconds(5)).instant("good", "q1", Instant.now())
			.range("broken", bad, Instant.now().minusSeconds(60), Instant.now(), Duration.ofSeconds(15))
			.execute();
		assertThat(results.ok("good")).isTrue();
		assertThat(results.instant("good").get(0).value()).isEqualTo(1.0);
		assertThat(results.ok("broken")).isFalse();
		assertThat(results.has("broken")).isTrue();
		assertThat(results.range("broken")).isEmpty();
		assertThat(results.allOk("good", "broken")).isFalse();
		// WARN 한 줄: 쿼리 이름과 짧은 원인만, 쿼리 원문 없음
		assertThat(output.getOut()).contains("Prometheus 쿼리 실패(broken: HTTP 400) — 그 항목만 비웁니다")
			.doesNotContain("bad_query_text");
	}

	@Test
	void readTimeoutOfOneQueryBlanksOnlyThatItem() {
		PrometheusClient shortRead = PrometheusClientTest.client(fake.url(), Duration.ofMillis(300));
		fake.respond("q1", "[{\"metric\":{},\"value\":[1,\"1\"]}]").delayQuery("slow", 1500);
		PromBatch.Results results = new PromBatch(shortRead, executor, System.nanoTime() + Duration.ofSeconds(5).toNanos())
			.instant("good", "q1", Instant.now())
			.instant("slow", "slow", Instant.now())
			.execute();
		assertThat(results.ok("good")).isTrue();
		assertThat(results.ok("slow")).isFalse();
		assertThat(results.instant("slow")).isEmpty();
	}

	@Test
	void deadlineFailsTheBatch() {
		fake.delay(1000);
		PromBatch batch = batch(Duration.ofMillis(200)).instant("a", "q1", Instant.now());
		long started = System.nanoTime();
		assertThatThrownBy(batch::execute).isInstanceOf(PrometheusException.class).hasMessage("갱신 시간 초과");
		assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(900));
	}

}
