package com.tetra.portal.domain.tenant;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 테넌트 스키마의 tenant_notification. TenantRouter.inTenant 안에서만 쓴다. */
public interface TenantNotificationRepository extends JpaRepository<TenantNotification, Long> {

	/** 최신순. 페이지네이션은 아직 없음(계획서 Q14). */
	List<TenantNotification> findAllByOrderByOccurredAtDescNotificationIdDesc();

	/** 읽음 표시 — 이미 읽었어도 1행(멱등). tenant_id도 WHERE에 넣는다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update TenantNotification n set n.read = true where n.notificationId = :id and n.tenantId = :tenantId")
	int markRead(@Param("id") Long id, @Param("tenantId") String tenantId);

}
