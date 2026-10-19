package com.tetra.portal.plan;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tetra.portal.domain.platform.TrafficPlan;
import com.tetra.portal.domain.platform.TrafficPlanRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 신청 화면(UI-TP-08 · 10)의 요금제 선택 · 예상 비용 계산. 화면 목업의 고정 단가(mocks/pricing.ts) 대신 쓴다. */
@Tag(name = "요금제", description = "신청 화면의 요금제 · 단가")
@RestController
@RequestMapping("/api/portal")
public class PlanController {

	private final TrafficPlanRepository plans;

	public PlanController(TrafficPlanRepository plans) {
		this.plans = plans;
	}

	public record PlanResponse(@Schema(description = "S · M · L · XL") String planCode,
			@Schema(description = "최대 동시 수용 인원") Long maxCapacity,
			@Schema(description = "트래픽 시간당 요금(원)") BigDecimal hourlyPrice,
			@Schema(description = "호스팅 시간당 요금(원) — 요금제마다 다르다") BigDecimal hostingHourlyPrice) {

		static PlanResponse of(TrafficPlan plan) {
			return new PlanResponse(plan.getPlanCode(), plan.getMaxCapacity(), plan.getHourlyPrice(),
					plan.getHostingHourlyPrice());
		}

	}

	@Operation(summary = "요금제 목록",
			description = "노출(is_visible)된 것만, 작은 것부터. 예상 비용 = ceil(호스팅 시간) × (hourlyPrice + hostingHourlyPrice)")
	@GetMapping("/plans")
	public List<PlanResponse> plans() {
		return plans.findByVisibleTrueOrderByMaxCapacityAsc().stream().map(PlanResponse::of).toList();
	}

}
