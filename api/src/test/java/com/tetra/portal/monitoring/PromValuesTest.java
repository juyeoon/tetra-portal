package com.tetra.portal.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.tetra.portal.monitoring.PrometheusClient.Point;
import com.tetra.portal.monitoring.PrometheusClient.Sample;
import com.tetra.portal.monitoring.PrometheusClient.Series;

/**
 * 1분 칸 만들기 — Pod별 누적 카운터의 차이(일반 · 재시작 · 새 Pod · 결측)와 게이지 칸. 칸 시각은 구간 시작이고,
 * 칸을 모두 더하면 Pod별 마지막 누적값의 합(재시작 전 값 포함)과 같다.
 */
class PromValuesTest {

	static final Instant T0 = Instant.parse("2026-10-28T01:00:00Z"); // 10:00 KST

	/** 격자 n분: 10:00, 10:01, … */
	static List<Long> grid(int minutes) {
		List<Long> grid = new ArrayList<>();
		for (int i = 0; i < minutes; i++) {
			grid.add(T0.plusSeconds(60L * i).toEpochMilli());
		}
		return grid;
	}

	/** 격자 시각마다 누적값(null = 그 시각 샘플 없음) */
	static Series series(Map<String, String> labels, Double... values) {
		List<Point> points = new ArrayList<>();
		for (int i = 0; i < values.length; i++) {
			if (values[i] != null) {
				points.add(new Point(T0.plusSeconds(60L * i).toEpochMilli(), values[i]));
			}
		}
		return new Series(labels, points);
	}

	static Map<String, String> pod(String name) {
		return Map.of("pod", name);
	}

	static Map<String, String> pod(String name, String result) {
		return Map.of("pod", name, "result", result);
	}

	static List<Long> slots(List<Series> range, List<Sample> now, int minutes) {
		return PromValues.perMinute(range, now, grid(minutes), null).get("");
	}

	@Test
	void 일반_칸마다_직전_값과의_차이_칸_시각은_구간_시작() {
		// 10:00 0 → 10:01 5 → 10:02 8 → 지금(10:02:40) 10
		List<Long> slots = slots(List.of(series(pod("a"), 0.0, 5.0, 8.0)), List.of(new Sample(pod("a"), 10.0)), 3);
		// 칸 10:00 = (10:00, 10:01] 5, 칸 10:01 = 3, 칸 10:02(지금까지) = 2
		assertThat(slots).containsExactly(5L, 3L, 2L);
		assertThat(PromValues.total(slots)).isEqualTo(10);
	}

	@Test
	void 첫_격자까지의_누적은_첫_칸에_넣는다() {
		List<Long> slots = slots(List.of(series(pod("a"), 4.0, 6.0)), List.of(new Sample(pod("a"), 6.0)), 2);
		assertThat(slots).containsExactly(6L, 0L);
		assertThat(PromValues.total(slots)).isEqualTo(6);
	}

	@Test
	void Pod_재시작으로_값이_줄면_지금_값을_그대로_더한다() {
		// 0 → 4 → (재시작) 1 → 3, 지금 3 — 재시작 전 4건도 빠지지 않는다
		List<Long> slots = slots(List.of(series(pod("a"), 0.0, 4.0, 1.0, 3.0)), List.of(new Sample(pod("a"), 3.0)), 4);
		assertThat(slots).containsExactly(4L, 1L, 2L, 0L);
		assertThat(PromValues.total(slots)).isEqualTo(4 + 3);
	}

	@Test
	void 새_Pod는_0부터() {
		// a: 0 → 2 → 4, 지금 4 / b: 10:02에 처음 3, 지금 5
		var range = List.of(series(pod("a"), 0.0, 2.0, 4.0), series(pod("b"), null, null, 3.0));
		var now = List.of(new Sample(pod("a"), 4.0), new Sample(pod("b"), 5.0));
		List<Long> slots = slots(range, now, 3);
		assertThat(slots).containsExactly(2L, 2L + 3L, 2L);
		assertThat(PromValues.total(slots)).isEqualTo(4 + 5);
	}

	@Test
	void 지금_값에만_있는_Pod도_0부터() {
		var range = List.of(series(pod("a"), 0.0, 2.0));
		var now = List.of(new Sample(pod("a"), 2.0), new Sample(pod("c"), 1.0));
		assertThat(slots(range, now, 2)).containsExactly(2L, 1L);
	}

	@Test
	void 결측은_건너뛰고_다음_값과의_차이에_넣는다() {
		// a: 0 → (없음) → 6, 지금 7. 빠진 칸의 증가분은 다음 칸으로
		List<Long> slots = slots(List.of(series(pod("a"), 0.0, null, 6.0)), List.of(new Sample(pod("a"), 7.0)), 3);
		assertThat(slots).containsExactly(0L, 6L, 1L);
		assertThat(PromValues.total(slots)).isEqualTo(7);
	}

	@Test
	void 값이_있는_시리즈가_하나도_없는_칸은_null() {
		List<Long> slots = slots(List.of(series(pod("a"), null, null, 4.0)), List.of(new Sample(pod("a"), 4.0)), 3);
		assertThat(slots).containsExactly(null, 4L, 0L);
		assertThat(PromValues.total(slots)).isEqualTo(4);
		assertThat(PromValues.total(Arrays.<Long>asList(null, null))).isNull();
		assertThat(PromValues.perMinute(List.of(), List.of(), grid(2), "result")).isEmpty();
	}

	@Test
	void 라벨별로_나누고_Pod를_합친다() {
		var range = List.of(series(pod("a", "success"), 0.0, 3.0), series(pod("a", "sold_out"), 0.0, 0.0),
				series(pod("b", "success"), 0.0, 1.0), series(pod("b", "sold_out"), 0.0, 1.0));
		var now = List.of(new Sample(pod("a", "success"), 3.0), new Sample(pod("a", "sold_out"), 2.0),
				new Sample(pod("b", "success"), 1.0), new Sample(pod("b", "sold_out"), 1.0));
		var byResult = PromValues.perMinute(range, now, grid(2), "result");
		assertThat(byResult.get("success")).containsExactly(4L, 0L);
		assertThat(byResult.get("sold_out")).containsExactly(1L, 2L);
	}

	@Test
	void 수집_간격_15초여도_막대_합이_누적값과_같다() {
		// 이벤트 12 모양: 10:00:13 · 10:00:28에 성공 → 10:01 격자 5, 10:04:43 · 10:04:58 → 10:05 격자 8, 10:05:28 → 10:06 격자 15
		// increase(...[1m])는 창 안 증가분을 60/45배로 외삽하고 창 사이(격자 직전 수집 ~ 격자)를 빠뜨렸다
		Double[] cumulative = { 0.0, 5.0, 5.0, 5.0, 5.0, 8.0, 15.0, 15.0 };
		List<Long> slots = slots(List.of(series(pod("a"), cumulative)), List.of(new Sample(pod("a"), 15.0)), 8);
		assertThat(slots).containsExactly(5L, 0L, 0L, 0L, 3L, 7L, 0L, 0L); // 10:00 · 10:04 · 10:05 — 결과 탭과 같은 분
		assertThat(PromValues.total(slots)).isEqualTo(15);
	}

	@Test
	void 게이지는_다음_격자_값을_이_칸으로_마지막_칸은_지금_값() {
		// 10:01 격자 값(10:00:5x 수집)은 10:00 분의 값 — 결과 탭 최대 동시 접속 시각(세션을 만든 분)과 같은 분
		var range = List.of(series(Map.of(), 1.0, 5.0, 19.0));
		assertThat(PromValues.gaugeByMinute(range, 20.0, grid(3))).containsExactly(5.0, 19.0, 20.0);
		assertThat(PromValues.gaugeByMinute(List.of(), 20.0, grid(2))).containsExactly(null, 20.0);
		assertThat(PromValues.gaugeByMinute(range, null, List.of())).isEmpty();
	}

}
