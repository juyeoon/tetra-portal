package com.tetra.portal.event.apply;

import java.time.OffsetDateTime;
import java.util.List;

import com.tetra.portal.domain.EventType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * UI-TP-07~10 신청 내용 (multipart의 `application` 파트, JSON). 배너는 `banner` 파트.
 * 재고는 이벤트 하나에 하나(stockCount) — 쿠폰별 수량은 없다(일괄 발급, 계획서 5-5).
 * 일시는 ISO-8601 +09:00(예: 2026-11-27T20:00:00+09:00). 초 아래는 버린다.
 */
@Schema(name = "ApplyRequest")
public record ApplyRequest(
		@Schema(maxLength = 100) @NotBlank(message = "필수 입력입니다.") @Size(max = 100, message = "100자 이하로 입력해 주세요.") String name,
		@NotNull(message = "필수 입력입니다.") EventType eventType,
		@NotNull(message = "필수 입력입니다.") OffsetDateTime startAt,
		@NotNull(message = "필수 입력입니다.") OffsetDateTime endAt,
		// TEST-LEAD 원래: @Schema(description = "지금 + 30분 이후, 이벤트 시작 − 100분까지. 호스팅 기간이 이벤트 기간을 덮어야 한다")@NotNull(message = "필수 입력입니다.") OffsetDateTime hostingStartAt,
		@Schema(description = "지금 + 10분 이후, 이벤트 시작 − 35분까지. 호스팅 기간이 이벤트 기간을 덮어야 한다")@NotNull(message = "필수 입력입니다.") OffsetDateTime hostingStartAt, // TEST-LEAD
		@NotNull(message = "필수 입력입니다.") OffsetDateTime hostingEndAt,
		@Schema(description = "GET /api/portal/plans 의 planCode") @NotBlank(message = "필수 입력입니다.") String trafficPlanCode,
		@Schema(description = "총 발급 재고 1~999,999,999") @NotNull(message = "필수 입력입니다.") @Min(value = 1, message = "1 이상이어야 합니다.") @Max(value = 999_999_999, message = "999,999,999 이하여야 합니다.") Long stockCount,
		@Schema(description = "발급 결과를 받을 고객사 페이지 http(s) 주소") @NotBlank(message = "필수 입력입니다.") @Size(max = 2048, message = "2048자 이하로 입력해 주세요.") String endpointUrl,
		@Schema(description = "COUPON이면 1~10개(입력 순서 = 노출 순서), 그 밖의 타입은 빈 목록") @NotNull(message = "필수 입력입니다.") @Size(max = 10, message = "쿠폰은 10개까지입니다.") List<@Valid CouponInput> coupons,
		@NotNull(message = "필수 입력입니다.") @Valid Layout layout) {

	public record CouponInput(
			@NotBlank(message = "필수 입력입니다.") @Size(max = 100, message = "100자 이하로 입력해 주세요.") String name,
			@NotBlank(message = "필수 입력입니다.") @Size(max = 500, message = "500자 이하로 입력해 주세요.") String description) {
	}

	/** 화면 구성 — DB CHECK(chk_event_layout_config)와 같은 모양. 이 record를 그대로 JSON으로 저장한다. */
	public record Layout(@NotNull(message = "필수 입력입니다.") @Valid Pos title,
			@NotNull(message = "필수 입력입니다.") @Valid Pos banner,
			@NotNull(message = "필수 입력입니다.") @Valid Pos couponList,
			@NotNull(message = "필수 입력입니다.") @Valid Pos issueButton,
			@NotNull(message = "필수 입력입니다.") Theme theme) {
	}

	public record Pos(@NotNull(message = "필수 입력입니다.") H h, @NotNull(message = "필수 입력입니다.") V v) {
	}

	public enum H {
		LEFT, CENTER, RIGHT
	}

	public enum V {
		TOP, MIDDLE, BOTTOM
	}

	public enum Theme {
		WHITE, WARM_ORANGE, DARK, BLUE
	}

}
