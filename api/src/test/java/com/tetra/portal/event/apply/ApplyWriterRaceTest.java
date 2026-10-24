package com.tetra.portal.event.apply;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.tetra.portal.support.IntegrationTest;
import com.tetra.portal.tenancy.TenantRouter;

/** 업로드 작업(b)과 정리 작업(c)이 겹칠 때 — 먼저 바꾼 쪽만 상태 로그 · 알림을 남긴다 (Console §7.13 한 사건에 1건). */
class ApplyWriterRaceTest extends IntegrationTest {

	@Autowired
	ApplyWriter writer;

	@Autowired
	StaleApplicationSweeper sweeper;

	@Autowired
	TenantRouter router;

	@Test
	void appliedAfterSweepLeavesOnlyFailureNotification() {
		data.tenant(A, "A사", "INACTIVE", "READY");
		data.event(1001, A).name("겨울 쿠폰").status("APPLYING")
			.appliedAt(LocalDateTime.now().minusMinutes(30).withNano(0)).insert();

		assertThat(sweeper.sweep()).isEqualTo(1);
		boolean applied = router.inTenant(A, () -> writer.markApplied(A, 1001L, "assets/banners/1001/banner.png"));

		assertThat(applied).isFalse();
		assertThat(data.registry(1001)).containsEntry("application_status", "APPLY_FAILED");
		assertThat(data.statusLogs(A, 1001)).hasSize(1);
		assertThat(data.operatorNotifications(1001)).singleElement()
			.satisfies(n -> assertThat(n).containsEntry("priority", "WARNING"));
		assertThat(data.tenantNotifications(A, 1001)).singleElement()
			.satisfies(n -> assertThat(n).containsEntry("priority", "WARNING"));
		assertThat(data.tenantStatusLogs(A)).isEmpty(); // 실패한 신청으로는 활성화하지 않는다
	}

	@Test
	void failedAfterAppliedLeavesOnlyAppliedNotification() {
		data.tenant(A, "A사", "ACTIVE", "READY");
		data.event(1001, A).name("겨울 쿠폰").status("APPLYING")
			.appliedAt(LocalDateTime.now().minusMinutes(30).withNano(0)).insert();

		assertThat(router.inTenant(A, () -> writer.markApplied(A, 1001L, "assets/banners/1001/banner.png"))).isTrue();
		assertThat(router.inTenant(A, () -> writer.markFailed(A, 1001L, BannerUploader.FAILED_DETAIL))).isFalse();
		assertThat(sweeper.sweep()).isZero();

		assertThat(data.registry(1001)).containsEntry("application_status", "APPLIED");
		assertThat(data.operatorNotifications(1001)).singleElement()
			.satisfies(n -> assertThat(n).containsEntry("priority", "INFO")
				.containsEntry("content", "[A사] [겨울 쿠폰] 이벤트 신청이 접수되었습니다. 승인 검토가 필요합니다."));
		assertThat(data.tenantNotifications(A, 1001)).singleElement()
			.satisfies(n -> assertThat(n).containsEntry("priority", "INFO"));
	}

}
