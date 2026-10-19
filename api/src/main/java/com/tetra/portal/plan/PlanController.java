package com.tetra.portal.plan;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tetra.portal.domain.platform.TrafficPlan;
import com.tetra.portal.domain.platform.TrafficPlanRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 신청 화면(UI-TP-08 · 10)의 요금제 선택.
 * 금액(시간당 · 호스팅 단가, 예상 비용)은 화면 · API에 내보내지 않는다(2026-10-19 결정 — 계획서 Q3). 단가는 DB에만 있고
 * 신청 때 event_billing에 기록된다.
 */
@Tag(name = "요금제", description = "신청 화면의 요금제 선택 (금액 없음)")
@RestController
@RequestMapping("/api/portal")
public class PlanController {

	private final TrafficPlanRepository plans;

	public PlanController(TrafficPlanRepository plans) {
		this.plans = plans;
	}

	public record PlanResponse(@Schema(description = "S · M · L · XL") String planCode,
			@Schema(description = "최대 동시 수용 인원") Long maxCapacity) {

		static PlanResponse of(TrafficPlan plan) {
			return new PlanResponse(plan.getPlanCode(), plan.getMaxCapacity());
		}

	}

	@Operation(summary = "요금제 목록", description = "노출(is_visible)된 것만, 작은 것부터. 금액은 없다")
	@GetMapping("/plans")
	public List<PlanResponse> plans() {
		return plans.findByVisibleTrueOrderByMaxCapacityAsc().stream().map(PlanResponse::of).toList();
	}

}
