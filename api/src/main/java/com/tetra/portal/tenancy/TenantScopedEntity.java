package com.tetra.portal.tenancy;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;

/** 테넌트 스키마 엔티티의 공통 부모. catalog를 지정하지 않는다 (현재 커넥션의 스키마를 쓴다). */
@MappedSuperclass
@EntityListeners(TenantGuardListener.class)
public abstract class TenantScopedEntity implements TenantOwned {

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "tenant_id", length = 12, nullable = false, updatable = false)
	protected String tenantId;

	@Override
	public String getTenantId() {
		return tenantId;
	}

}
