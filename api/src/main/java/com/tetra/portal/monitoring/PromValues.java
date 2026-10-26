package com.tetra.portal.monitoring;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.tetra.portal.monitoring.PrometheusClient.Sample;
import com.tetra.portal.monitoring.PrometheusClient.Series;

/** Prometheus 결과 다듬기(시각 격자 · 라벨별 값 · 반올림, Console OPS-05 PromValues 이식). 시리즈가 없으면 null이 원칙 */
final class PromValues {

	private PromValues() {
	}

	/** 같은 격자(step 배수)로 맞춘 구간. 모든 range 쿼리가 같은 시각을 쓰게 한다 */
	record Grid(Instant start, Instant end, Duration step) {

		static Grid of(Instant from, Instant to, Duration step) {
			long stepMillis = step.toMillis();
			Instant end = Instant.ofEpochMilli(Math.floorDiv(to.toEpochMilli(), stepMillis) * stepMillis);
			Instant start = Instant.ofEpochMilli(Math.floorDiv(from.toEpochMilli(), stepMillis) * stepMillis);
			return new Grid(start.isAfter(end) ? end : start, end, step);
		}

		List<Long> timestamps() {
			List<Long> times = new ArrayList<>();
			for (long t = start.toEpochMilli(); t <= end.toEpochMilli(); t += step.toMillis()) {
				times.add(t);
			}
			return times;
		}

	}

	static Double single(List<Sample> samples) {
		return samples.isEmpty() ? null : samples.get(0).value();
	}

	/** 시리즈 하나(sum · max 등 라벨 없는 집계)를 격자 값 배열로. 시리즈가 없으면 모두 null, missingIsZero면 모두 0 */
	static List<Double> onGrid(List<Series> series, List<Long> grid, boolean missingIsZero) {
		Map<Long, Double> values = new HashMap<>();
		if (!series.isEmpty()) {
			series.get(0).points().forEach(p -> values.put(p.epochMillis(), p.value()));
		}
		List<Double> out = new ArrayList<>(grid.size());
		for (Long t : grid) {
			Double v = values.get(t);
			out.add(v == null && missingIsZero ? Double.valueOf(0) : v);
		}
		return out;
	}

	/** 라벨 값이 contains인 시리즈 하나를 격자로(예: uri에 "claim") */
	static List<Double> onGrid(List<Series> series, String label, String contains, List<Long> grid) {
		List<Series> matched = series.stream()
			.filter(s -> s.labels().getOrDefault(label, "").contains(contains))
			.limit(1)
			.toList();
		return onGrid(matched, grid, false);
	}

}
