package com.tetra.portal.monitoring;

import java.time.Duration;
import java.time.LocalDateTime;

import com.tetra.portal.domain.EventStatus;
import com.tetra.portal.domain.HostingStatus;

/**
 * 모니터링 상태(명세 4절). 요청마다 현재 시각으로 정한다 — 캐시한 Prometheus 값에 묶지 않아 경계 시각에 바로 바뀐다.
 * 종료 = hosting_end_at(WF4). 발급은 hosting_end_at까지 열려 있고 end_at으로 막는 곳이 없다(2026-10-27 결정, Console EventPhase와 같음).
 * 준비(WF2-2) 실패(OPEN_FAILED)는 종료 전까지 "입장 전"처럼 보인다(응답 prepareFailed로 안내 문구).
 * 배포(WF1-2) · 호스팅 시작(WF2-1) 실패는 열리지 않은 이벤트 — 목록에서 빼고 상세는 UNAVAILABLE(재실행으로 회복되면 다시 시각대로).
 */
public enum MonitoringPhase {

	/** 지금 &lt; 입장. 진행 단계만, Prometheus 조회 안 함 */
	BEFORE_ENTRY,
	/** 입장 ≤ 지금 &lt; 시작 */
	ENTRY_OPEN,
	/** 시작 ≤ 지금 &lt; 종료, 재고 &gt; 0(또는 모름) */
	LIVE,
	/** 시작 뒤 남은 재고 0, 종료 전 */
	SOLD_OUT,
	/** 종료(hosting_end_at) 이후 또는 WF4 결과. Prometheus 조회 안 함 */
	ENDED,
	/** hosting DEPLOY_FAILED · HOSTING_FAILED — 열리지 않아 모니터링할 수 없음. 목록에 없고 Prometheus 조회 안 함 */
	UNAVAILABLE;

	/**
	 * 입장 = 시작 − 60분. issuance-api entry-open-before · web ENTRY_OPEN_LEAD_MINUTES · Console EntryOpen.ENTRY_OPEN_LEAD와 같은 값
	 */
	// TEST-LEAD 원래: static final Duration ENTRY_OPEN_LEAD = Duration.ofMinutes(60);
	static final Duration ENTRY_OPEN_LEAD = Duration.ofMinutes(5); // TEST-LEAD

	static LocalDateTime entryAt(LocalDateTime startAt) {
		return startAt.minus(ENTRY_OPEN_LEAD);
	}

	/** 시각 · DB 상태만으로 정한다. 품절은 Prometheus 값을 본 뒤 {@link #withRemaining}. 배포 · 호스팅 시작 실패를 먼저 본다 */
	static MonitoringPhase of(LocalDateTime startAt, LocalDateTime hostingEndAt, EventStatus eventStatus,
			HostingStatus hostingStatus, LocalDateTime now) {
		if (hostingStatus == HostingStatus.DEPLOY_FAILED || hostingStatus == HostingStatus.HOSTING_FAILED) {
			return UNAVAILABLE;
		}
		if (eventStatus == EventStatus.EXPIRED || hostingStatus == HostingStatus.ENDED
				|| hostingStatus == HostingStatus.END_FAILED || !now.isBefore(hostingEndAt)) {
			return ENDED;
		}
		if (eventStatus == EventStatus.OPEN_FAILED || now.isBefore(entryAt(startAt))) {
			return BEFORE_ENTRY;
		}
		return now.isBefore(startAt) ? ENTRY_OPEN : LIVE;
	}

	/** 진행 중인데 남은 재고가 0이면 품절. 재고를 모르면(null) 그대로 */
	MonitoringPhase withRemaining(Long remaining) {
		return this == LIVE && remaining != null && remaining == 0 ? SOLD_OUT : this;
	}

	/** Prometheus를 조회하는 상태(데이터는 입장부터) */
	boolean queriesPrometheus() {
		return this == ENTRY_OPEN || this == LIVE || this == SOLD_OUT;
	}

}
