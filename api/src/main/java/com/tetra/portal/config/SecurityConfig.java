package com.tetra.portal.config;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;

import com.tetra.portal.auth.CookieBearerTokenResolver;
import com.tetra.portal.auth.JwtTokenService;
import com.tetra.portal.common.audit.AuditContext;
import com.tetra.portal.common.error.ErrorCode;
import com.tetra.portal.common.error.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * /api/** 는 JWT 쿠키(TETRA_PORTAL_TOKEN) 인증. 로그인 · 로그아웃 · 비밀번호 설정 · 헬스 체크만 공개.
 * 그 밖(화면 파일 · SPA 경로)은 같은 이미지에 든 화면이라 인증 없이 연다.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder, JsonMapper jsonMapper)
			throws Exception {
		http
			// 서버 세션 없음 (태스크 여러 개). 인증은 JWT 쿠키만.
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			// CSRF 토큰 대신 (계획서 Q5): 쿠키 SameSite=Strict(다른 사이트에서 온 요청에는 쿠키가 안 붙음)
			// + 상태를 바꾸는 API는 JSON 본문만 받는다(다른 사이트의 form 전송은 JSON을 보낼 수 없음)
			.csrf(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.requestCache(AbstractHttpConfigurer::disable)
			// HSTS는 모든 응답에. 기본값은 request.isSecure()일 때만인데, forward-headers-strategy: none이라 ALB(TLS 종료) 뒤
			// 요청이 http로 보인다 — 브라우저는 https로만 오므로(ALB가 TLS만 받음) 늘 붙여도 된다
			.headers(headers -> headers
				.httpStrictTransportSecurity(hsts -> hsts.requestMatcher(AnyRequestMatcher.INSTANCE)))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.POST, "/api/portal/auth/login", "/api/portal/auth/logout").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/portal/password/setup", "/api/portal/password/setup/verify",
						"/api/portal/support-mail")
				.permitAll()
				.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
				.requestMatchers("/api/**", "/actuator/**").authenticated()
				.anyRequest().permitAll())
			.oauth2ResourceServer(oauth -> oauth
				.bearerTokenResolver(new CookieBearerTokenResolver())
				.jwt(jwt -> jwt.decoder(jwtDecoder))
				.authenticationEntryPoint((request, response, ex) -> write(request, response, jsonMapper, ErrorCode.UNAUTHENTICATED)))
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint((request, response, e) -> write(request, response, jsonMapper, ErrorCode.UNAUTHENTICATED))
				.accessDeniedHandler((request, response, e) -> write(request, response, jsonMapper, ErrorCode.FORBIDDEN)));
		return http.build();
	}

	@Bean
	JwtDecoder jwtDecoder(JwtTokenService tokens) {
		return tokens.decoder();
	}

	/** BCrypt — Console과 같은 알고리즘(테넌트 비밀번호는 Portal만 쓰지만 형식을 맞춘다). */
	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	private static void write(HttpServletRequest request, HttpServletResponse response, JsonMapper jsonMapper,
			ErrorCode code) throws IOException {
		// 감사 줄의 reason = 응답 본문의 code (보안 체인에서 끝난 401 · 403)
		AuditContext.of(request).reason(code.name());
		response.setStatus(code.status().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		jsonMapper.writeValue(response.getOutputStream(), ErrorResponse.of(code));
	}

}
