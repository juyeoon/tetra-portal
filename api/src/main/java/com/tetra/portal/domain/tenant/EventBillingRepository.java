package com.tetra.portal.domain.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

/** 테넌트 스키마의 event_billing(이벤트와 1:1). TenantRouter.inTenant 안에서만 쓴다. */
public interface EventBillingRepository extends JpaRepository<EventBilling, Long> {

}
