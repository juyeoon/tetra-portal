package com.tetra.portal.notification;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tetra.portal.auth.CurrentTenant;
import com.tetra.portal.common.error.ApiErrorCodes;
import com.tetra.portal.common.error.ErrorCode;
import com.tetra.portal.notification.NotificationRules.Chip;
import com.tetra.portal.notification.NotificationService.ListCondition;
import com.tetra.portal.notification.NotificationService.NotificationPage;
import com.tetra.portal.notification.NotificationService.Priority;
import com.tetra.portal.notification.NotificationService.UnreadCount;
import com.tetra.portal.tenancy.TenantRouter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/** UI-TP-13 알림센터. 필터 · 검색 · 페이지 · 건수는 서버에서. */
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

	@Operation(summary = "알림 목록",
			description = "최신순(같은 시각이면 나중 알림 먼저) 한 페이지와 화면 건수. 조건은 모두 AND. 범위 밖 page는 빈 items(전체 수 · 페이지 수는 그대로)")
	@ApiErrorCodes({ ErrorCode.INVALID_INPUT, ErrorCode.TENANT_NOT_READY })
	@GetMapping
	public NotificationPage list(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
			@Parameter(description = "URGENT · WARNING · INFO. 없으면 전체") @RequestParam(required = false) Priority priority,
			@Parameter(description = "APPLICATION · EVENT · REPORT. 없으면 전체") @RequestParam(required = false) Chip chip,
			@Parameter(description = "안 읽은 것만") @RequestParam(defaultValue = "false") boolean unreadOnly,
			@Parameter(description = "내용 부분 일치 — 앞뒤 공백 · 대소문자 무시, "
					+ NotificationService.QUERY_MAX + "자 이하") @RequestParam(required = false) String q,
			@Parameter(description = "1부터, " + NotificationService.PAGE_MAX + " 이하") @RequestParam(defaultValue = "1") int page,
			@Parameter(description = "1~" + NotificationService.SIZE_MAX) @RequestParam(defaultValue = ""
					+ NotificationService.PAGE_SIZE) int size) {
		ListCondition condition = new ListCondition(priority, chip, unreadOnly, q, page, size);
		return router.inTenant(CurrentTenant.id(jwt), () -> service.list(condition));
	}

	@Operation(summary = "안 읽은 알림 수", description = "사이드바 배지. 목록 응답 unreadCount와 같은 값")
	@ApiErrorCodes({ ErrorCode.TENANT_NOT_READY })
	@GetMapping("/unread-count")
	public UnreadCount unreadCount(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
		return router.inTenant(CurrentTenant.id(jwt), () -> new UnreadCount(service.unreadCount()));
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

	@Operation(summary = "모두 읽음", description = "그 테넌트의 안 읽은 알림 전부(현재 페이지만이 아님). 안 읽은 알림이 없어도 204")
	@ApiErrorCodes({ ErrorCode.TENANT_NOT_READY })
	@PatchMapping("/read-all")
	public ResponseEntity<Void> markAllRead(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
		String tenantId = CurrentTenant.id(jwt);
		router.inTenant(tenantId, () -> service.markAllRead(tenantId));
		return ResponseEntity.noContent().build();
	}

}
