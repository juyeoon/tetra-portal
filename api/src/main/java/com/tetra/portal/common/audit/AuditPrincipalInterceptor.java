package com.tetra.portal.common.audit;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.HandlerInterceptor;

import com.tetra.portal.auth.CurrentTenant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 인증된 요청의 JWT sub(tenant_id)를 AuditContext에 옮긴다. AuditFilter는 보안 체인 바깥이라 끝날 때는 보안 컨텍스트가
 * 이미 비워져 있으므로, 체인 안(MVC)에서 미리 복사해 둔다. 공개 경로(로그아웃)도 쿠키가 유효하면 여기서 채워진다.
 */
public class AuditPrincipalInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof Jwt jwt) {
			AuditContext.of(request).tenant(CurrentTenant.id(jwt));
		}
		return true;
	}

}
