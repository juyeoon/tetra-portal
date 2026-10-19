package com.tetra.portal.event.apply;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 1분마다 멈춘 신청 정리. 시험에서는 끈다(tetra.apply.sweep-enabled=false) — 시험은 sweep()을 직접 부른다. */
@Component
@ConditionalOnProperty(name = "tetra.apply.sweep-enabled", havingValue = "true", matchIfMissing = true)
class StaleApplicationScheduler {

	private final StaleApplicationSweeper sweeper;

	StaleApplicationScheduler(StaleApplicationSweeper sweeper) {
		this.sweeper = sweeper;
	}

	@Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
	void run() {
		sweeper.sweep();
	}

}
