package com.tetra.portal.tenancy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;

/**
 * 이중 확인. 테넌트 스키마에서 읽거나 쓰는 행의 tenant_id가 라우팅한 tenant_id와 다르면 예외를 던진다.
 * 라우팅 없이(공용 스키마 컨텍스트에서) 테넌트 행을 다루는 것도 막는다.
 */
public class TenantGuardListener {

	private static final Logger log = LoggerFactory.getLogger(TenantGuardListener.class);

	@PostLoad
	@PrePersist
	void verify(Object entity) {
		if (!(entity instanceof TenantOwned owned)) {
			return;
		}
		String routed = TenantContext.current();
		if (routed == null || !routed.equals(owned.getTenantId())) {
			log.error("테넌트 불일치: entity={}, routedTenant={}, rowTenant={}", entity.getClass().getSimpleName(), routed,
					owned.getTenantId());
			throw new TenantMismatchException();
		}
	}

}
