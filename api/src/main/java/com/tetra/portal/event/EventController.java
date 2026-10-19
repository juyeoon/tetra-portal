package com.tetra.portal.event;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tetra.portal.auth.CurrentTenant;
import com.tetra.portal.common.error.ApiErrorCodes;
import com.tetra.portal.common.error.ErrorCode;
import com.tetra.portal.domain.platform.Tenant;
import com.tetra.portal.tenancy.TenantRouter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * UI-TP-05 이벤트 내역 · UI-TP-06 상세. 로그인한 테넌트의 스키마만 읽는다 —
 * 다른 테넌트의 eventId를 넣어도 자기 스키마에 없으므로 404.
 */
@Tag(name = "이벤트", description = "UI-TP-05 목록 · UI-TP-06 상세")
@RestController
@RequestMapping("/api/portal/events")
public class EventController {

	private final TenantRouter router;

	private final EventReader reader;

	public EventController(TenantRouter router, EventReader reader) {
		this.router = router;
		this.reader = reader;
	}

	@Operation(summary = "UI-TP-05 이벤트 목록", description = "최근 신청 먼저. 정렬 · 필터는 화면에서")
	@ApiErrorCodes({ ErrorCode.TENANT_NOT_READY })
	@GetMapping
	public List<EventResponses.Summary> list(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
		return router.inTenant(CurrentTenant.id(jwt), reader::list);
	}

	@Operation(summary = "UI-TP-06 이벤트 상세", description = "신청 내용 · 쿠폰 · 화면 구성 · 비용 · 도메인(승인 후) · 진행 기록")
	@ApiErrorCodes({ ErrorCode.INVALID_INPUT, ErrorCode.EVENT_NOT_FOUND, ErrorCode.TENANT_NOT_READY })
	@GetMapping("/{eventId}")
	public EventResponses.Detail detail(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
			@PathVariable Long eventId) {
		Tenant tenant = router.findTenant(CurrentTenant.id(jwt));
		return router.inTenant(tenant, () -> reader.detail(eventId, tenant));
	}

}
