package com.tetra.portal.tenancy;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * 스키마를 정하는 유일한 곳.
 * Hibernate 세션(=트랜잭션)이 열릴 때 한 번 불리고, 그 세션의 커넥션은 끝까지 이 스키마를 쓴다.
 */
public class SchemaTenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

	@Override
	public String resolveCurrentTenantIdentifier() {
		String tenantId = TenantContext.current();
		if (tenantId == null) {
			return TenantIds.PLATFORM_SCHEMA;
		}
		return "t_" + TenantIds.requireValid(tenantId);
	}

	@Override
	public boolean validateExistingCurrentSessions() {
		return true;
	}

	@Override
	public boolean isRoot(String schema) {
		return false;
	}

}
