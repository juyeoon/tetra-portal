package com.tetra.portal.domain.tenant;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 테넌트 스키마의 tenant_notification. TenantRouter.inTenant 안에서만 쓴다.
 * 알림센터 목록(조건 · 페이지)은 Specification으로 만든다(NotificationService).
 */
public interface TenantNotificationRepository
		extends JpaRepository<TenantNotification, Long>, JpaSpecificationExecutor<TenantNotification> {

	/** 알림센터 건수 재료 — 분류 값 · 우선순위 · 읽음별 건수와 그중 이벤트가 있는 건수(칩 판정은 NotificationRules) */
	@Query("""
			select n.category as category, n.priority as priority, n.read as read,
				count(n) as total, count(n.eventId) as withEvent
			from TenantNotification n
			group by n.category, n.priority, n.read""")
	List<CountGroup> countGroups();

	interface CountGroup {

		String getCategory();

		String getPriority();

		Boolean getRead();

		Long getTotal();

		/** total 중 event_id가 있는 건수 */
		Long getWithEvent();

	}

	/** 읽음 표시 — 이미 읽었어도 1행(멱등). tenant_id도 WHERE에 넣는다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update TenantNotification n set n.read = true where n.notificationId = :id and n.tenantId = :tenantId")
	int markRead(@Param("id") Long id, @Param("tenantId") String tenantId);

	/** 모두 읽음 — 안 읽은 알림 전부(없으면 0행). tenant_id도 WHERE에 넣는다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update TenantNotification n set n.read = true where n.read = false and n.tenantId = :tenantId")
	int markAllRead(@Param("tenantId") String tenantId);

}
