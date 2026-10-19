package com.tetra.portal.domain.platform;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantStatusLogRepository extends JpaRepository<TenantStatusLog, Long> {

}
