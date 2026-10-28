package com.tetra.portal.monitoring;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

	/*
	 * 1분 칸: 격자 시각 grid[j]가 칸 j의 시작(결과 탭 분당 묶음과 같은 분). 칸 j = (grid[j], grid[j+1]], 마지막 칸 = (grid[n-1], 지금].
	 * 범위 쿼리의 grid[j+1] 값은 그 직전 수집(칸 j 분 안)에서 온다 — 끝 시각에 붙이면 1분 밀린다(이벤트 12: 10:00 → 10:01).
	 */

	/**
	 * Pod별 누적 카운터(범위 1분 격자 + 지금 값) → 칸마다 증가분(정수).
	 * 시리즈(Pod × 라벨)마다 직전 값과의 차이: 값이 줄면(재시작) 지금 값 그대로, 처음 나타나면 0부터, 빠진 시각(결측)은 건너뛰어
	 * 다음 값과의 차이에 넣는다. 첫 칸에는 grid[0]까지의 누적도 넣는다(카운터는 Pod 기동 때 0으로 등록).
	 * → 칸을 모두 더하면 Pod별 누적값(재시작 전 값 포함)의 합과 같다. increase()처럼 외삽하거나 창 사이를 빠뜨리지 않는다.
	 * 그 칸에 값이 있는 시리즈가 하나도 없으면 null.
	 *
	 * @param group 나눌 라벨(예: result) — 값별로 칸 목록 하나. null이면 모두 합친다(키 "")
	 */
	static Map<String, List<Long>> perMinute(List<Series> range, List<Sample> now, List<Long> grid, String group) {
		int n = grid.size();
		Map<Map<String, String>, Double[]> cumulative = new LinkedHashMap<>();
		for (Series s : range) {
			Map<Long, Double> byTime = new HashMap<>();
			s.points().forEach(p -> byTime.put(p.epochMillis(), p.value()));
			Double[] values = cumulative.computeIfAbsent(s.labels(), k -> new Double[n + 1]);
			for (int i = 0; i < n; i++) {
				values[i] = byTime.get(grid.get(i));
			}
		}
		for (Sample s : now) {
			cumulative.computeIfAbsent(s.labels(), k -> new Double[n + 1])[n] = s.value();
		}
		Map<String, List<Long>> out = new LinkedHashMap<>();
		if (n == 0) {
			return out;
		}
		for (Map.Entry<Map<String, String>, Double[]> e : cumulative.entrySet()) {
			String key = group == null ? "" : e.getKey().getOrDefault(group, "");
			List<Long> slots = out.computeIfAbsent(key, k -> new ArrayList<>(Collections.nCopies(n, (Long) null)));
			Long last = null;
			for (int i = 0; i <= n; i++) {
				Double v = e.getValue()[i];
				if (v == null) {
					continue;
				}
				long c = Math.round(v);
				long delta = last == null || c < last ? c : c - last;
				last = c;
				// 값 i(grid[i] 또는 지금)는 칸 i-1의 끝. grid[0]까지의 누적은 첫 칸으로
				int slot = Math.max(0, i - 1);
				Long before = slots.get(slot);
				slots.set(slot, (before == null ? 0 : before) + delta);
			}
		}
		return out;
	}

	/**
	 * 게이지 범위(1분 격자) + 지금 값 → 칸마다 값. 칸 j = grid[j+1] 시각 값(칸 j 분에 수집한 값), 마지막 칸 = 지금 값.
	 * 유효 세션 게이지는 수집한 그 분의 창 합이라 결과 탭 최대 동시 접속 시각(세션을 만든 분)과 같은 분이 된다.
	 */
	static List<Double> gaugeByMinute(List<Series> range, Double now, List<Long> grid) {
		if (grid.isEmpty()) {
			return List.of();
		}
		List<Double> values = onGrid(range, grid, false);
		List<Double> out = new ArrayList<>(values.subList(1, values.size()));
		out.add(now);
		return out;
	}

	/** 칸 값의 합. 모두 null이면 null */
	static Long total(List<Long> slots) {
		Long sum = null;
		for (Long v : slots) {
			if (v != null) {
				sum = (sum == null ? 0 : sum) + v;
			}
		}
		return sum;
	}

}
