package com.tetra.portal.domain.platform;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantRepository extends JpaRepository<Tenant, String> {

	/** 로그인 — email은 uk_tenant_email로 유일하다. */
	Optional<Tenant> findByEmail(String email);

	/** 첫 비밀번호 설정 — 아직 비밀번호가 없을 때만 (조건부 UPDATE). */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Tenant t set t.password = :newHash where t.tenantId = :tenantId and t.password is null")
	int setPasswordIfUnset(@Param("tenantId") String tenantId, @Param("newHash") String newHash);

	/** 첫 신청 성공 때 자동 활성화 — INACTIVE일 때만 (조건부 UPDATE, 이미 ACTIVE면 0행). */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Tenant t set t.accountStatus = com.tetra.portal.domain.AccountStatus.ACTIVE
			 where t.tenantId = :tenantId and t.accountStatus = com.tetra.portal.domain.AccountStatus.INACTIVE""")
	int activateIfInactive(@Param("tenantId") String tenantId);

	/** UI-TP-14 이메일 알림 켜기 · 끄기. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Tenant t set t.emailNotificationEnabled = :enabled where t.tenantId = :tenantId")
	int updateEmailNotification(@Param("tenantId") String tenantId, @Param("enabled") boolean enabled);

	/** 재설정 링크(나중에) — 확인한 해시 그대로일 때만 (조건부 UPDATE). */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Tenant t set t.password = :newHash where t.tenantId = :tenantId and t.password = :currentHash")
	int changePasswordIfUnchanged(@Param("tenantId") String tenantId, @Param("currentHash") String currentHash,
			@Param("newHash") String newHash);

}
