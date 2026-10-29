package com.tetra.portal.result;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.tetra.portal.domain.EventStatus;
import com.tetra.portal.domain.HostingStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * UI-TP-06 결과 탭(UI-TP-6-2). Console OPS-04-3 결과 응답과 같은 판정 · 같은 숫자 — 두 화면이 같은 숫자를 보여 준다.
 * 종료 처리 실패의 원인(Console failure)은 내리지 않는다(2026-10-29 — 고객 화면은 "확인 중").
 * state로 화면이 상태 화면을 고르고, state별로 쓰지 않는 값은 null이다.
 */
@Schema(name = "EventResult")
public record ResultResponse(Long eventId, State state, OffsetDateTime eventOpenAt,
		@Schema(description = "발급 이력 집계 시각 — READY일 때만") OffsetDateTime aggregatedAt, HostingStatus hostingStatus,
		EventStatus eventStatus, @Schema(description = "이벤트 종료 예정 — IN_PROGRESS일 때만") OffsetDateTime endAt,
		@Schema(description = "READY일 때만") Result result) {

	/** 위에서부터 먼저 맞는 것 (Console과 같은 순서). */
	public enum State {

		@Schema(description = "종료 처리 실패 — 결과를 만들지 못함(화면은 확인 중, 원인은 내리지 않음)")
		END_FAILED,
		@Schema(description = "결과 · CSV 준비됨")
		READY,
		@Schema(description = "이벤트는 끝났고 집계 중")
		AGGREGATING,
		@Schema(description = "아직 진행 전 · 진행 중 (승인 전 신청도 여기)")
		IN_PROGRESS

	}

	public record Result(Metrics metrics, Composition composition, List<MinuteBucket> perMinute) {
	}

	/** 핵심 지표. 비율은 0~1 소수. capacityUsageRate = peakConcurrent / maxCapacity. */
	public record Metrics(Long successCount, Long totalStock, BigDecimal selloutRate, Long soldoutCount,
			Long peakConcurrent, Long maxCapacity, BigDecimal capacityUsageRate, OffsetDateTime peakAt) {
	}

	/**
	 * 발급 결과 구성. 이탈 = session_count − success_count − soldout_count (0 미만이면 0) —
	 * 입장했지만 결과(발급 · 품절)를 받지 못한 수. Console OPS-04-3과 같은 기준.
	 */
	public record Composition(Long successCount, Long soldoutCount,
			@Schema(description = "이탈 = session_count − successCount − soldoutCount (0 미만이면 0). 입장했지만 결과(발급 · 품절)를 받지 못한 수") Long abandonedCount) {
	}

	/** 대기열 진입 시각(queue_entered_at)을 분 단위로 묶은 SUCCESS · FAILED_SOLDOUT 건수. 화면이 앞의 몇 분만 그려도 된다. */
	public record MinuteBucket(OffsetDateTime minute, long successCount, long soldoutCount) {
	}

	/** 발급 이력 CSV 다운로드 링크 — 열면 파일로 저장된다. 만료 뒤에는 다시 요청. */
	public record CsvDownload(String url, OffsetDateTime expiresAt) {

		@Override
		public String toString() {
			return "CsvDownload[url=****, expiresAt=" + expiresAt + "]";
		}

	}

}
