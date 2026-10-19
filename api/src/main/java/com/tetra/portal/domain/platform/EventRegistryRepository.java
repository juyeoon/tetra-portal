package com.tetra.portal.domain.platform;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tetra.portal.domain.ApplicationStatus;

public interface EventRegistryRepository extends JpaRepository<EventRegistry, Long> {

	/**
	 * 신청 상태 변경(조건부). 업로드 작업과 정리 작업이 겹쳐도 먼저 바꾼 쪽만 1행 — 나머지는 0행이라 아무것도 하지 않는다.
	 * tenant_id도 WHERE에 넣어 라우팅한 테넌트의 행만 바꾼다.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update EventRegistry r set r.applicationStatus = :to
			 where r.eventId = :eventId and r.tenantId = :tenantId and r.applicationStatus = :from""")
	int updateApplicationStatus(@Param("eventId") Long eventId, @Param("tenantId") String tenantId,
			@Param("from") ApplicationStatus from, @Param("to") ApplicationStatus to);

	/** 정리 대상: 배너 업로드를 기다리다 멈춘 신청(모든 테넌트). */
	List<EventRegistry> findByApplicationStatusAndAppliedAtBefore(ApplicationStatus status, LocalDateTime before);

}
