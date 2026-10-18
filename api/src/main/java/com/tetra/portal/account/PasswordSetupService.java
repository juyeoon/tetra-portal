package com.tetra.portal.account;

import java.time.Clock;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tetra.portal.common.error.ApiException;
import com.tetra.portal.common.error.ErrorCode;
import com.tetra.portal.domain.platform.Tenant;
import com.tetra.portal.domain.platform.TenantRepository;
import com.tetra.portal.tenancy.TenantIds;

/**
 * UI-TP-04 비밀번호 설정 — WF5(테넌트 가입)의 마지막 단계. 메일 링크는 Console이 만든다.
 * 공용 스키마(tetra_platform.tenant)만 바꾸므로 테넌트 준비(READY) 전에도 설정할 수 있다. 로그인은 READY 뒤에.
 * 링크가 틀린 이유(형식 · 만료 · 위조 · 이미 사용 · 없는 테넌트)는 구분하지 않고 410 하나로 응답한다.
 */
@Service
public class PasswordSetupService {

	private final TenantRepository tenants;

	private final PasswordLinkVerifier verifier;

	private final PasswordEncoder passwordEncoder;

	private final Clock clock;

	public PasswordSetupService(TenantRepository tenants, PasswordLinkVerifier verifier,
			PasswordEncoder passwordEncoder, Clock clock) {
		this.tenants = tenants;
		this.verifier = verifier;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	/** 링크 확인 — 맞으면 설정 화면에 보여 줄 이메일. */
	@Transactional(readOnly = true)
	public String verify(String tenantId, long exp, String sig) {
		return validTenant(tenantId, exp, sig).getEmail();
	}

	@Transactional
	public void setup(String tenantId, long exp, String sig, String rawPassword) {
		Tenant tenant = validTenant(tenantId, exp, sig);
		String newHash = passwordEncoder.encode(rawPassword);
		// 확인한 그 해시일 때만 바꾼다 — 같은 링크로 동시에 두 번 설정해도 한 번만 성공
		int updated = tenant.getPassword() == null ? tenants.setPasswordIfUnset(tenantId, newHash)
				: tenants.changePasswordIfUnchanged(tenantId, tenant.getPassword(), newHash);
		if (updated != 1) {
			throw new ApiException(ErrorCode.PASSWORD_LINK_INVALID);
		}
	}

	private Tenant validTenant(String tenantId, long exp, String sig) {
		if (!TenantIds.isValid(tenantId) || exp < clock.instant().getEpochSecond()) {
			throw new ApiException(ErrorCode.PASSWORD_LINK_INVALID);
		}
		Tenant tenant = tenants.findById(tenantId).orElse(null);
		if (tenant == null || !verifier.matches(tenantId, exp, tenant.getPassword(), sig)) {
			throw new ApiException(ErrorCode.PASSWORD_LINK_INVALID);
		}
		return tenant;
	}

}
