package com.tetra.portal.event.apply;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

import com.tetra.portal.external.BannerImage;
import com.tetra.portal.external.BannerStorage;
import com.tetra.portal.tenancy.TenantRouter;

/**
 * WF1-1 ④ 커밋 뒤 비동기 배너 업로드 → (b) APPLIED / (c) APPLY_FAILED.
 * 배너는 메모리에 있으므로 태스크가 내려가면 사라진다 — 종료 때 진행 중 업로드를 기다리고(TaskConfig),
 * 그래도 남은 신청은 정리 작업(StaleApplicationSweeper)이 실패 처리한다.
 */
@Component
class BannerUploader {

	static final String FAILED_DETAIL = "배너 업로드 실패";

	private static final Logger log = LoggerFactory.getLogger(BannerUploader.class);

	private final TaskExecutor executor;

	private final BannerStorage storage;

	private final TenantRouter router;

	private final ApplyWriter writer;

	BannerUploader(@Qualifier("bannerUploadExecutor") TaskExecutor executor, BannerStorage storage,
			TenantRouter router, ApplyWriter writer) {
		this.executor = executor;
		this.storage = storage;
		this.router = router;
		this.writer = writer;
	}

	void submit(String tenantId, Long eventId, BannerImage image) {
		try {
			executor.execute(() -> upload(tenantId, eventId, image));
		}
		catch (TaskRejectedException ex) {
			// 대기열이 가득 참(업로드가 몰림) — 기다리게 두지 않고 바로 실패로 기록해 다시 신청하게 한다
			log.warn("배너 업로드 대기열이 가득 차 실패 처리: eventId={}", eventId);
			fail(tenantId, eventId);
		}
	}

	void upload(String tenantId, Long eventId, BannerImage image) {
		String key;
		try {
			key = storage.put(tenantId, eventId, image);
		}
		catch (RuntimeException ex) {
			log.warn("배너 업로드 실패: eventId={}, tenantId={}", eventId, tenantId, ex);
			fail(tenantId, eventId);
			return;
		}
		try {
			if (!router.inTenant(tenantId, () -> writer.markApplied(tenantId, eventId, key))) {
				log.info("이미 처리된 신청이라 APPLIED로 바꾸지 않음: eventId={}", eventId);
			}
		}
		catch (RuntimeException ex) {
			// 신청은 APPLYING으로 남는다 → 정리 작업이 실패 처리
			log.error("APPLIED 기록 실패: eventId={}", eventId, ex);
		}
	}

	private void fail(String tenantId, Long eventId) {
		try {
			router.inTenant(tenantId, () -> writer.markFailed(tenantId, eventId, FAILED_DETAIL));
		}
		catch (RuntimeException ex) {
			log.error("APPLY_FAILED 기록 실패: eventId={}", eventId, ex);
		}
	}

}
