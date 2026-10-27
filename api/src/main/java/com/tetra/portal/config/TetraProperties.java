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
		@Valid @NotNull @DefaultValue Apply apply,
		@Valid @NotNull @DefaultValue Csv csv,
		@Valid @NotNull @DefaultValue Mail mail,
		@Valid @NotNull @DefaultValue Monitoring monitoring,
		@Valid @NotNull @DefaultValue Event event,
		@NotBlank @DefaultValue("http://localhost:5174") String publicUrl) {

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

	/** 다운로드 링크(presigned URL) 만료 — CSV_PRESIGN_TTL, Console과 같은 기본 5분. */
	public record Csv(@NotNull @DefaultValue("PT5M") Duration presignTtl) {
	}

	/**
	 * 메일(SES). from = 보내는 주소(MAIL_FROM, SES 인증 도메인 tetraops.cloud 주소여야 한다),
	 * supportTo = 로그인 전 문의 메일을 받을 운영팀 주소(MAIL_SUPPORT_TO).
	 * supportTo = tetra.ops.1@gmail.com (2026-10-19 확정, 계획서 Q13). SES 샌드박스라 받는 주소도 인증돼 있어야 한다.
	 */
	public record Mail(@NotBlank @DefaultValue("Tetra <no-reply@tetraops.cloud>") String from,
			@NotBlank @DefaultValue("tetra.ops.1@gmail.com") String supportTo) {
	}

	/**
	 * 모니터링(UI-TP-11) — Prometheus HTTP API(PROMETHEUS_URL, 인증 없음). 비어 있어도 기동하고 모니터링만 stale로 응답한다.
	 * 기본값은 Console tetra.monitoring과 같다. cacheTtl = 이벤트별 Prometheus 결과 캐시, refreshTimeout = 갱신 1회 전체 상한.
	 */
	public record Monitoring(@DefaultValue("") String prometheusUrl,
			@NotNull @DefaultValue("PT2S") Duration connectTimeout,
			@NotNull @DefaultValue("PT3S") Duration readTimeout,
			@NotNull @DefaultValue("PT15S") Duration cacheTtl,
			@NotNull @DefaultValue("PT8S") Duration refreshTimeout) {

		public boolean configured() {
			return prometheusUrl != null && !prometheusUrl.isBlank();
		}

	}

	/**
	 * 이벤트 주소 {slug}.{tenant_id}.{rootDomain} 의 뒷부분 — 플랫폼 도메인(EVENT_ROOT_DOMAIN, 인프라 플랜 D2).
	 * 테넌트의 root_domain(고객사 원래 도메인, 등록 때 받는 값)은 이벤트 주소에 쓰지 않는다.
	 */
	public record Event(@NotBlank @DefaultValue("tetraops.cloud") String rootDomain) {
	}

	/** 테넌트 콘텐츠 버킷 — WF5가 테넌트마다 만든다(계획서 2-1 ②). Portal은 이름만 만든다. */
	public String storageBucket(String tenantId) {
		return "tetra-storage-" + env + "-" + tenantId;
	}

	/** 테넌트 감사 로그 버킷 — 발급 이력 CSV · 리포트(플랜 D22). WF5가 만든다. */
	public String auditBucket(String tenantId) {
		return "tetra-audit-logs-" + env + "-" + tenantId;
	}

}
