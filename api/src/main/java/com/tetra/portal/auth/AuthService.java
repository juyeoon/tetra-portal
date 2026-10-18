package com.tetra.portal.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.tetra.portal.common.error.ApiException;
import com.tetra.portal.common.error.ErrorCode;
import com.tetra.portal.domain.platform.Tenant;
import com.tetra.portal.domain.platform.TenantRepository;

/**
 * 테넌트 관리자 로그인 (계획서 4-1). 잠금 · MFA 없음.
 * 로그인할 수 있는 테넌트: 비밀번호가 설정됨 + provisioning_status = READY. account_status는 보지 않는다
 * (INACTIVE 테넌트도 첫 신청을 해야 ACTIVE가 되므로 로그인할 수 있어야 한다).
 * 실패 이유(없는 이메일 · 틀린 비밀번호 · 비밀번호 미설정 · 준비 중)는 구분하지 않고 401 LOGIN_FAILED 하나로 응답한다.
 */
@Service
public class AuthService {

	/** 없는 이메일에도 해시 비교를 한 번 해서 응답 시간으로 계정 존재 여부가 드러나지 않게 한다. */
	private static final String DUMMY_HASH = "$2a$10$T1EpFVINGQ000xopLK.afuBaAeaA9nmyd86nRTJTlVHRuKD1lx0Re";

	private final TenantRepository tenants;

	private final PasswordEncoder passwordEncoder;

	private final JwtTokenService tokens;

	public AuthService(TenantRepository tenants, PasswordEncoder passwordEncoder, JwtTokenService tokens) {
		this.tenants = tenants;
		this.passwordEncoder = passwordEncoder;
		this.tokens = tokens;
	}

	public record LoginResult(Tenant tenant, String token) {

		@Override
		public String toString() {
			return "LoginResult[tenantId=" + tenant.getTenantId() + ", token=****]";
		}

	}

	public LoginResult login(String email, String rawPassword) {
		Tenant tenant = tenants.findByEmail(email).orElse(null);
		String hash = tenant == null || tenant.getPassword() == null ? DUMMY_HASH : tenant.getPassword();
		boolean matches = passwordEncoder.matches(rawPassword, hash);
		if (tenant == null || tenant.getPassword() == null || !matches || !tenant.isReady()) {
			throw new ApiException(ErrorCode.LOGIN_FAILED);
		}
		return new LoginResult(tenant, tokens.issue(tenant.getTenantId()));
	}

	/** 토큰이 가리키는 테넌트. 그 사이 지워졌거나 준비 상태가 아니게 되면 다시 로그인하게 한다. */
	public Tenant currentTenant(String tenantId) {
		return tenants.findById(tenantId)
			.filter(Tenant::isReady)
			.orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
	}

}
