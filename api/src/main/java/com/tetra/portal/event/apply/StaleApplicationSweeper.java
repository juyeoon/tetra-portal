package com.tetra.portal.event.apply;

import java.time.Clock;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.tetra.portal.common.time.Kst;
import com.tetra.portal.config.TetraProperties;
import com.tetra.portal.domain.ApplicationStatus;
import com.tetra.portal.domain.platform.EventRegistry;
import com.tetra.portal.domain.platform.EventRegistryRepository;
import com.tetra.portal.tenancy.TenantRouter;

/**
 * WF1-1 정리 — APPLYING으로 staleAfter(기본 10분) 넘게 남은 신청을 APPLY_FAILED로 바꾼다
 * (업로드 도중 태스크 재시작 · 장애로 메모리의 배너가 사라진 경우). 테넌트가 다시 신청하면 된다.
 * 태스크가 여러 개여도 상태 변경이 조건부라 한 번만 기록된다. 주기 실행은 StaleApplicationScheduler.
 */
@Component
public class StaleApplicationSweeper {

	static final String TIMEOUT_DETAIL = "배너 업로드 시간 초과";

	private static final Logger log = LoggerFactory.getLogger(StaleApplicationSweeper.class);

	private final EventRegistryRepository registries;

	private final TenantRouter router;

	private final ApplyWriter writer;

	private final TetraProperties properties;

	private final Clock clock;

	StaleApplicationSweeper(EventRegistryRepository registries, TenantRouter router, ApplyWriter writer,
			TetraProperties properties, Clock clock) {
		this.registries = registries;
		this.router = router;
		this.writer = writer;
		this.properties = properties;
		this.clock = clock;
	}

	/** 실패 처리한 건수. */
	public int sweep() {
		LocalDateTime before = Kst.now(clock).minus(properties.apply().staleAfter());
		int failed = 0;
		for (EventRegistry r : registries.findByApplicationStatusAndAppliedAtBefore(ApplicationStatus.APPLYING,
				before)) {
			try {
				if (router.inTenant(r.getTenantId(), () -> writer.markFailed(r.getTenantId(), r.getEventId(),
						TIMEOUT_DETAIL))) {
					failed++;
				}
			}
			catch (RuntimeException ex) {
				log.error("멈춘 신청 정리 실패: eventId={}", r.getEventId(), ex);
			}
		}
		if (failed > 0) {
			log.warn("배너 업로드가 끝나지 않은 신청 {}건을 APPLY_FAILED로 정리", failed);
		}
		return failed;
	}

}
