package com.tetra.portal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 모든 값은 환경 변수로 들어온다(인프라 약속 4절). 비어 있으면 기동이 실패한다.
 * 비밀 값(JWT · 비밀번호 링크 서명 키)은 P1에서 추가하고, 비밀을 담은 record는 toString을 덮어쓴다.
 */
@Validated
@ConfigurationProperties(prefix = "tetra")
public record TetraProperties(@NotBlank String env, @Valid @NotNull Aws aws) {

	public record Aws(@NotBlank String region) {
	}

}
