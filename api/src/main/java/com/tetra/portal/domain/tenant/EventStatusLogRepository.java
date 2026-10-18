package com.tetra.portal.domain.tenant;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** 테넌트 스키마의 event_status_log. TenantRouter.inTenant 안에서만 쓴다. */
public interface EventStatusLogRepository extends JpaRepository<EventStatusLog, Long> {

	/** UI-TP-06 진행 기록. 시각 순서, 같은 시각이면 기록 순서. */
	List<EventStatusLog> findByEventIdOrderByChangedAtAscLogIdAsc(Long eventId);

}
