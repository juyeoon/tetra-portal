package com.tetra.portal.domain.tenant;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** 테넌트 스키마의 event. TenantRouter.inTenant 안에서만 쓴다. */
public interface EventRepository extends JpaRepository<Event, Long> {

	/** UI-TP-05 목록. 최근 신청 먼저(화면이 다시 정렬할 수 있다). */
	List<Event> findAllByOrderByAppliedAtDescEventIdDesc();

}
