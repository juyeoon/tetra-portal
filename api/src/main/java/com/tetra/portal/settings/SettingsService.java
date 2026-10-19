package com.tetra.portal.settings;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tetra.portal.common.error.ApiException;
import com.tetra.portal.common.error.ErrorCode;
import com.tetra.portal.domain.platform.TenantRepository;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 공용 스키마(tetra_platform.tenant)만 다루므로 테넌트 스키마로 라우팅하지 않는다.
 * TODO(확정 필요, 계획서 Q15): email_notification_enabled를 보고 실제로 알림 메일을 보내는 주체(Console · 운영 자동화)와 내용.
 */
@Service
public class SettingsService {

	private final TenantRepository tenants;

	SettingsService(TenantRepository tenants) {
		this.tenants = tenants;
	}

	public record SettingsResponse(@Schema(description = "알림을 이메일로도 받기") boolean emailEnabled,
			@Schema(description = "Slack 연동 제공 여부 — MVP 1차는 false") boolean slackAvailable) {
	}

	@Transactional(readOnly = true)
	public SettingsResponse get(String tenantId) {
		var tenant = tenants.findById(tenantId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
		return new SettingsResponse(tenant.isEmailNotificationEnabled(), false);
	}

	@Transactional
	public SettingsResponse setEmail(String tenantId, boolean enabled) {
		if (tenants.updateEmailNotification(tenantId, enabled) == 0) {
			throw new ApiException(ErrorCode.UNAUTHENTICATED);
		}
		return new SettingsResponse(enabled, false);
	}

}
