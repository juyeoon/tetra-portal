package com.tetra.portal.domain.tenant;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tetra.portal.domain.ApplicationStatus;

/** 테넌트 스키마의 event. TenantRouter.inTenant 안에서만 쓴다. */
public interface EventRepository extends JpaRepository<Event, Long> {

	/** UI-TP-05 목록. 최근 신청 먼저(화면이 다시 정렬할 수 있다). */
	List<Event> findAllByOrderByAppliedAtDescEventIdDesc();

	/** UI-TP-11 모니터링 드롭다운 — 승인된(호스팅 대상) 이벤트. 묶음 · 순서는 화면 규칙으로 서비스가 정한다. */
	List<Event> findByApplicationStatus(ApplicationStatus applicationStatus);

	// ---- 신청 결과(WF1-1 트랜잭션 b · c). APPLYING일 때만 바꾼다(조건부 — 업로드 작업과 정리 작업이 겹쳐도 한 번만).
	//      tenant_id도 WHERE에 넣어 라우팅한 테넌트의 행만 바꾼다. ----

	/** chk_event_banner: APPLIED와 배너 경로를 한 UPDATE에서 함께 채운다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Event e
			   set e.applicationStatus = com.tetra.portal.domain.ApplicationStatus.APPLIED,
			       e.bannerImagePath = :bannerPath
			 where e.eventId = :eventId and e.tenantId = :tenantId
			   and e.applicationStatus = com.tetra.portal.domain.ApplicationStatus.APPLYING""")
	int markApplied(@Param("eventId") Long eventId, @Param("tenantId") String tenantId,
			@Param("bannerPath") String bannerPath);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Event e
			   set e.applicationStatus = com.tetra.portal.domain.ApplicationStatus.APPLY_FAILED
			 where e.eventId = :eventId and e.tenantId = :tenantId
			   and e.applicationStatus = com.tetra.portal.domain.ApplicationStatus.APPLYING""")
	int markApplyFailed(@Param("eventId") Long eventId, @Param("tenantId") String tenantId);

}
