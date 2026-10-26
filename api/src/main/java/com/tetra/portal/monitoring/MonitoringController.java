package com.tetra.portal.monitoring;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tetra.portal.auth.CurrentTenant;
import com.tetra.portal.common.error.ApiErrorCodes;
import com.tetra.portal.common.error.ErrorCode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/** UI-TP-11 모니터링 — 로그인한 테넌트의 승인된 이벤트만. 15초마다 갱신되는 근사값(확정 결과는 결과 탭). */
@Tag(name = "모니터링", description = "UI-TP-11 이벤트 실시간 현황")
@RestController
@RequestMapping("/api/portal/monitoring/events")
public class MonitoringController {

	private final MonitoringService service;

	public MonitoringController(MonitoringService service) {
		this.service = service;
	}

	@Operation(summary = "드롭다운 목록",
			description = "진행 중(입장 ~ 종료) → 오늘 예정 → 최근 7일 종료. defaultEventId = 기본 선택. Prometheus를 읽지 않는다")
	@ApiErrorCodes({ ErrorCode.TENANT_NOT_READY })
	@GetMapping
	public MonitoringResponses.EventList events(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
		return service.events(CurrentTenant.id(jwt));
	}

	@Operation(summary = "이벤트 현황",
			description = "state: BEFORE_ENTRY · ENTRY_OPEN · LIVE · SOLD_OUT · ENDED. kpi · series는 ENTRY_OPEN · LIVE · SOLD_OUT에서만"
					+ "(그 밖은 null, Prometheus 조회 안 함). stale = 데이터 지연(값은 마지막 정상 값)")
	@ApiErrorCodes({ ErrorCode.INVALID_INPUT, ErrorCode.EVENT_NOT_FOUND, ErrorCode.TENANT_NOT_READY })
	@GetMapping("/{eventId}")
	public MonitoringResponses.EventMonitoring event(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
			@PathVariable Long eventId) {
		return service.event(CurrentTenant.id(jwt), eventId);
	}

}
