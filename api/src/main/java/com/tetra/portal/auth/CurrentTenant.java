package com.tetra.portal.auth;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 인증된 요청의 tenant_id. 테넌트 스키마 라우팅(TenantRouter)에 넘기는 값은 여기서만 가져온다 —
 * 요청 경로 · 본문의 tenant_id로 라우팅하지 않는다(다른 테넌트 데이터 접근 차단).
 */
public final class CurrentTenant {

	private CurrentTenant() {
	}

	public static String id(Jwt jwt) {
		return jwt.getSubject();
	}

}
