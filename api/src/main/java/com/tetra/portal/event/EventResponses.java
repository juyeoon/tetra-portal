package com.tetra.portal.event;

import java.time.OffsetDateTime;
import java.util.List;

import com.tetra.portal.domain.ApplicationStatus;
import com.tetra.portal.domain.EventStatus;
import com.tetra.portal.domain.EventType;
import com.tetra.portal.domain.HostingStatus;
import com.tetra.portal.domain.StatusType;
import com.tetra.portal.result.ResultResponse;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.JsonNode;

/**
 * UI-TP-05 목록 · UI-TP-06 상세 응답. 상태 값은 DB 값 그대로(한글 표기는 화면), 일시는 ISO +09:00.
 * 금액(단가 · 예상 비용)은 내보내지 않는다(2026-10-19 결정 — 계획서 Q3). DB event_billing에는 신청 때 기록된다.
 */
public final class EventResponses {

	private EventResponses() {
	}

	@Schema(name = "EventSummary", description = "목록의 한 줄")
	public record Summary(Long eventId, String name, EventType eventType,
			@Schema(description = "신청 상태") ApplicationStatus applicationStatus,
			@Schema(description = "호스팅 상태 — 승인 전 null") HostingStatus hostingStatus,
			@Schema(description = "이벤트 상태 — 승인 전 null") EventStatus eventStatus, OffsetDateTime startAt,
			OffsetDateTime endAt, OffsetDateTime hostingStartAt, OffsetDateTime hostingEndAt, OffsetDateTime appliedAt,
			String trafficPlanCode) {
	}

	@Schema(name = "EventDetail")
	public record Detail(Long eventId, String name, EventType eventType, OffsetDateTime startAt, OffsetDateTime endAt,
			OffsetDateTime hostingStartAt, OffsetDateTime hostingEndAt,
			@Schema(description = "고객사 이벤트 페이지 주소(발급 결과를 받을 곳)") String endpointUrl,
			String trafficPlanCode, @Schema(description = "총 발급 재고(이벤트 단위)") Long stockCount,
			OffsetDateTime appliedAt, @Schema(description = "신청자(테넌트 담당자 이름)") String applicantName,
			Status status, List<CouponItem> coupons,
			@Schema(description = "화면 구성 — {title|banner|couponList|issueButton: {h, v}, theme}") JsonNode layout,
			@Schema(description = "이벤트 주소 {slug}.{tenant_id}.{root_domain} — 승인되고 만료 전일 때만, 아니면 null") String domain,
			@Schema(description = "진행 기록(신청 · 호스팅 · 이벤트), 시각 순서") List<LogItem> logs) {
	}

	public record Status(ApplicationStatus applicationStatus, HostingStatus hostingStatus, EventStatus eventStatus,
			@Schema(description = "반려 사유 — REJECTED일 때만") String rejectReason,
			@Schema(description = "결과 탭 상태 — 결과 API의 state와 같다. IN_PROGRESS면 결과 탭을 막는다") ResultResponse.State resultState) {
	}

	public record CouponItem(Long couponId, String name, String description) {
	}

	/** 누가 바꿨나 — 화면이 표시 문구로 바꾼다(예: TENANT → "{담당자} (테넌트 관리자)"). */
	public enum Actor {
		@Schema(description = "테넌트 관리자 — 신청 제출")
		TENANT,
		@Schema(description = "운영자 — 승인 · 반려")
		OPERATOR,
		@Schema(description = "시스템 — 배너 업로드 결과, 배포 · 오픈 · 종료 자동화")
		SYSTEM
	}

	public record LogItem(StatusType statusType, String prevStatus, String newStatus, OffsetDateTime changedAt,
			@Schema(description = "원인 · 사유 (실패 원인 등)") String detail, Actor actor) {
	}

}
