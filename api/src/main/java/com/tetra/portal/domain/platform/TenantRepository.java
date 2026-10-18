package com.tetra.portal.domain.platform;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, String> {

	/** 로그인(P1) — email은 uk_tenant_email로 유일하다. */
	Optional<Tenant> findByEmail(String email);

}
