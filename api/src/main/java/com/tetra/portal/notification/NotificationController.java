package com.tetra.portal.notification;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tetra.portal.auth.CurrentTenant;
import com.tetra.portal.common.error.ApiErrorCodes;
import com.tetra.portal.common.error.ErrorCode;
import com.tetra.portal.tenancy.TenantRouter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/** UI-TP-13 알림센터 (헤더 알림 · 알림 화면). 필터 · 검색은 화면에서. */
@Tag(name = "알림", description = "UI-TP-13")
@RestController
@RequestMapping("/api/portal/notifications")
public class NotificationController {

	private final TenantRouter router;

	private final NotificationService service;

	public NotificationController(TenantRouter router, NotificationService service) {
		this.router = router;
		this.service = service;
	}

	@Operation(summary = "알림 목록", description = "최신순 전체(아직 페이지네이션 없음)")
	@ApiErrorCodes({ ErrorCode.TENANT_NOT_READY })
	@GetMapping
	public List<NotificationService.NotificationItem> list(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
		return router.inTenant(CurrentTenant.id(jwt), service::list);
	}

	@Operation(summary = "읽음 표시", description = "이미 읽은 알림이어도 204")
	@ApiErrorCodes({ ErrorCode.INVALID_INPUT, ErrorCode.NOTIFICATION_NOT_FOUND, ErrorCode.TENANT_NOT_READY })
	@PatchMapping("/{notificationId}/read")
	public ResponseEntity<Void> markRead(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
			@PathVariable Long notificationId) {
		String tenantId = CurrentTenant.id(jwt);
		router.inTenant(tenantId, () -> service.markRead(tenantId, notificationId));
		return ResponseEntity.noContent().build();
	}

}
