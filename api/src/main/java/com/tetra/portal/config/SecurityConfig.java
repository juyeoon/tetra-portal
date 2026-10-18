package com.tetra.portal.config;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import com.tetra.portal.common.error.ErrorCode;
import com.tetra.portal.common.error.ErrorResponse;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * P0: /api/** 와 헬스 체크를 뺀 /actuator/** 는 모두 401. 그 밖은 같은 이미지에 든 화면 파일과 SPA 경로라 연다.
 * P1에서 JWT 쿠키 인증(로그인 · 비밀번호 설정 공개 경로 포함)을 붙인다 — 계획서 4-1.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper jsonMapper) throws Exception {
		http
			// 서버 세션 없음 (태스크 여러 개). 인증은 JWT 쿠키만(P1).
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			// TODO(P1): CSRF — 인터넷 공개라 Console보다 중요. SameSite=Strict 쿠키 + JSON 전용으로 시작(계획서 Q5)
			.csrf(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.requestCache(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
				.requestMatchers("/api/**", "/actuator/**").authenticated()
				.anyRequest().permitAll())
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint((request, response, e) -> write(response, jsonMapper, ErrorCode.UNAUTHENTICATED))
				.accessDeniedHandler((request, response, e) -> write(response, jsonMapper, ErrorCode.FORBIDDEN)));
		return http.build();
	}

	private static void write(HttpServletResponse response, JsonMapper jsonMapper, ErrorCode code) throws IOException {
		response.setStatus(code.status().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		jsonMapper.writeValue(response.getOutputStream(), ErrorResponse.of(code));
	}

}
