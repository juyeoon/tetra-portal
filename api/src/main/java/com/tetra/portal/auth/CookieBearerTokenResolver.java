package com.tetra.portal.auth;

import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.web.util.WebUtils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 테넌트 관리자 JWT는 Authorization 헤더가 아니라 HttpOnly 쿠키로 온다.
 * 인증이 필요한 경로(/api/**, /actuator/**)에서만 읽는다. 화면 파일 요청에 만료된 쿠키가 붙어 와도 401이 나지 않게 한다.
 */
public class CookieBearerTokenResolver implements BearerTokenResolver {

	@Override
	public String resolve(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		if (!path.startsWith("/api/") && !path.startsWith("/actuator/")) {
			return null;
		}
		Cookie cookie = WebUtils.getCookie(request, JwtTokenService.COOKIE_NAME);
		if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
			return null;
		}
		return cookie.getValue();
	}

}
