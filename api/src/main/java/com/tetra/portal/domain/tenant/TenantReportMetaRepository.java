package com.tetra.portal.domain.tenant;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** 테넌트 스키마의 tenant_report_meta. TenantRouter.inTenant 안에서만 쓴다. */
public interface TenantReportMetaRepository extends JpaRepository<TenantReportMeta, Long> {

	List<TenantReportMeta> findAllByOrderByCreatedAtDescReportIdDesc();

}
