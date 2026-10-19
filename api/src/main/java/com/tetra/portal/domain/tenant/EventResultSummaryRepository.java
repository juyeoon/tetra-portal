package com.tetra.portal.domain.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

/** 테넌트 스키마의 event_result_summary. TenantRouter.inTenant 안에서만 쓴다. */
public interface EventResultSummaryRepository extends JpaRepository<EventResultSummary, Long> {

}
