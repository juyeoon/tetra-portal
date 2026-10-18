package com.tetra.portal.tenancy;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.tetra.portal.common.error.ApiException;
import com.tetra.portal.common.error.ErrorCode;
import com.tetra.portal.domain.platform.Tenant;
import com.tetra.portal.domain.platform.TenantRepository;

/**
 * 테넌트 스키마(t_{tenant_id})로 들어가는 유일한 입구.
 * Portal은 로그인한 테넌트 하나만 다루므로 tenant_id는 인증(JWT sub)에서만 온다 — 요청 경로 · 본문의 값을 쓰지 않는다.
 * provisioning_status가 READY가 아니면 테넌트 스키마로 라우팅하지 않는다(409). 이 클래스는 트랜잭션 밖에서 불러야 한다.
 * 스키마 이름 자체는 SchemaTenantIdentifierResolver가 정한다.
 */
@Component
public class TenantRouter {

	private final TenantRepository tenants;

	public TenantRouter(TenantRepository tenants) {
		this.tenants = tenants;
	}

	/** tenantId로 테넌트를 찾는다(공용 스키마). 형식이 틀리면 없는 것으로 본다. */
	public Tenant findTenant(String tenantId) {
		if (!TenantIds.isValid(tenantId)) {
			throw new ApiException(ErrorCode.TENANT_NOT_FOUND);
		}
		return tenants.findById(tenantId).orElseThrow(() -> new ApiException(ErrorCode.TENANT_NOT_FOUND));
	}

	/** 테넌트 스키마에서 work를 실행한다. READY가 아니면 409. */
	public <T> T inTenant(Tenant tenant, Supplier<T> work) {
		if (!tenant.isReady()) {
			throw new ApiException(ErrorCode.TENANT_NOT_READY);
		}
		return TenantContext.callAs(tenant.getTenantId(), work);
	}

	/** tenantId로 찾아 테넌트 스키마에서 work를 실행한다. */
	public <T> T inTenant(String tenantId, Supplier<T> work) {
		return inTenant(findTenant(tenantId), work);
	}

	public void inTenant(String tenantId, Runnable work) {
		inTenant(tenantId, () -> {
			work.run();
			return null;
		});
	}

}
