package com.tetra.portal.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 모든 값은 환경 변수로 들어온다(인프라 약속 4절). 비어 있으면 기동이 실패한다.
 * 비밀 값(auth.jwtSecret, passwordLink.hmacSecret)은 기본값이 없고, toString에 나오지 않게 덮어쓴다.
 */
@Validated
@ConfigurationProperties(prefix = "tetra")
public record TetraProperties(
		@NotBlank String env,
		@Valid @NotNull Aws aws,
		@Valid @NotNull Auth auth,
		@Valid @NotNull PasswordLink passwordLink,
		@Valid @NotNull @DefaultValue Apply apply) {

	public record Aws(@NotBlank String region) {
	}

	/** PORTAL_JWT_SECRET — Portal 전용. Console 운영자 토큰 키(OPS_JWT_SECRET)와 다른 값이어야 한다. */
	public record Auth(@NotBlank @Size(min = 32, message = "32바이트 이상이어야 합니다.") String jwtSecret) {
		@Override
		public String toString() {
			return "Auth[jwtSecret=****]";
		}
	}

	/** PASSWORD_LINK_HMAC_SECRET — Console이 링크를 서명할 때 쓰는 값과 같아야 한다(같은 금고). */
	public record PasswordLink(@NotBlank String hmacSecret) {
		@Override
		public String toString() {
			return "PasswordLink[hmacSecret=****]";
		}
	}

	/** 신청(WF1-1). staleAfter: 배너 업로드를 기다리다 APPLY_FAILED로 정리하는 시간. */
	public record Apply(@NotNull @DefaultValue("PT10M") Duration staleAfter, @DefaultValue("true") boolean sweepEnabled) {
	}

	/** 테넌트 콘텐츠 버킷 — WF5가 테넌트마다 만든다(계획서 2-1 ②). Portal은 이름만 만든다. */
	public String storageBucket(String tenantId) {
		return "tetra-storage-" + env + "-" + tenantId;
	}

}
