package com.tetra.portal.monitoring;

import java.time.OffsetDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * UI-TP-11 모니터링 응답(명세 6절). 15초마다 갱신되는 근사값 — 확정 결과는 결과 탭(DB).
 * 값이 없으면 null(화면 "–"), 0은 0. 수치(5xx · p95 · Pod)는 내리지 않는다(명세 0절).
 */
public final class MonitoringResponses {

	private MonitoringResponses() {
	}

	/** 드롭다운 묶음 — 이 순서로 보인다 */
	public enum Group {

		@Schema(description = "입장 ~ 종료(end_at)")
		LIVE,
		@Schema(description = "오늘(KST) 시작, 아직 입장 전")
		TODAY,
		@Schema(description = "종료(end_at) 7일 안")
		ENDED

	}

	@Schema(name = "MonitoringEventList")
	public record EventList(List<EventItem> events,
			@Schema(description = "기본 선택: 진행 중 → 입장 중 → 가장 가까운 예정 → 가장 최근 종료. 목록이 비면 null") Long defaultEventId) {
	}

	/** state는 시각 기준(목록은 Prometheus를 읽지 않아 SOLD_OUT이 나오지 않는다). */
	@Schema(name = "MonitoringEventItem")
	public record EventItem(Long eventId, String name, Group group, MonitoringPhase state, boolean prepareFailed,
			OffsetDateTime entryAt, OffsetDateTime startAt, @Schema(description = "이벤트 종료(end_at)") OffsetDateTime endAt) {
	}

	/**
	 * 이벤트 하나. state · serverTime · period · steps · header.endsInSec는 요청마다 현재 시각으로 계산하고,
	 * Prometheus 값(kpi · series · rejects5m)만 이벤트별로 15초 캐시한다.
	 * kpi · series는 ENTRY_OPEN · LIVE · SOLD_OUT에서만(BEFORE_ENTRY · ENDED · UNAVAILABLE은 null, Prometheus 조회 안 함).
	 * ENDED도 결과 집계가 끝날(완료 · 실패) 때까지 화면이 15초마다 다시 부른다 — 진행 단계만 바뀐다(DB).
	 * UNAVAILABLE(배포 · 호스팅 시작 실패)은 steps가 빈 목록이고 header 값도 모두 null — 화면은 빈 상태만.
	 */
	@Schema(name = "EventMonitoring")
	public record EventMonitoring(Long eventId, String name, MonitoringPhase state,
			@Schema(description = "준비(WF2-2) 실패 — 화면은 입장 전처럼 보이고 안내 문구만. 사유는 내리지 않는다") boolean prepareFailed,
			@Schema(description = "데이터 지연 — Prometheus 조회 실패. 값은 마지막 정상 값(없으면 null)") boolean stale,
			@Schema(description = "마지막으로 Prometheus 조회에 성공한 시각") OffsetDateTime lastUpdatedAt,
			@Schema(description = "응답 시각 — 남은 시간 계산용(브라우저 시계 차이 방지)") OffsetDateTime serverTime,
			Period period, List<Step> steps, Header header, Kpi kpi, Series series,
			@Schema(description = "입장 실패(JWT 검증 실패 · 재사용) 최근 5분") Long rejects5m) {
	}

	/** 입장 = 시작 − 60분. 종료 = endAt(상태 · 남은 시간 기준). hostingEndAt은 결과 집계 칸에만 쓴다. */
	public record Period(OffsetDateTime hostingStartAt, OffsetDateTime entryAt, OffsetDateTime startAt,
			@Schema(description = "이벤트 종료(발급 마감) — 상태 판정 · 남은 시간 기준") OffsetDateTime endAt,
			@Schema(description = "호스팅 종료(WF4) — 결과 집계 칸 예정 시각") OffsetDateTime hostingEndAt,
			@Schema(description = "승인 · 만료 전일 때만") String domain) {
	}

	/** END = 이벤트 종료(end_at). 호스팅 종료는 단계로 두지 않는다(결과 집계 칸의 예정 시각) */
	public enum StepKey {

		HOSTING_START, ENTRY, LIVE, END, AGGREGATE

	}

	public enum StepStatus {

		DONE,
		@Schema(description = "지금 실제로 진행 중인 단계만 — 예정 시각 전 기다리는 구간은 UPCOMING")
		CURRENT, UPCOMING,
		@Schema(description = "입장(OPEN_FAILED이거나 OPEN이 한 번도 안 된 채 종료) · 결과 집계(END_FAILED). 사유는 내리지 않는다")
		FAILED,
		@Schema(description = "진행 안 됨 — OPEN이 한 번도 안 된 채 끝난 이벤트의 이벤트 진행 · 이벤트 종료 · 결과 집계")
		SKIPPED

	}

	/**
	 * at = 그 단계 예정 시각(이벤트 종료 = end_at, 결과 집계 = hosting_end_at — "이후 집계"). 완료는 실제 기록(상태 · 전이 로그)으로 정한다.
	 * AGGREGATE는 hosting_end_at이 지났거나 종료 기록이 있으면 CURRENT(집계 중), 결과가 준비되면 DONE. actualAt은 호스팅 시작 · 입장만.
	 */
	public record Step(StepKey step, OffsetDateTime at,
			@Schema(description = "실제 시각 — 처음 HOSTING · OPEN이 된 시각(상태 로그). 아직이거나 로그가 없으면 null") OffsetDateTime actualAt,
			StepStatus status,
			@Schema(description = "예정 시각이 지났는데 아직 — 호스팅 시작 · 입장만(HOSTING · OPEN 전, 실패 제외)") boolean delayed) {
	}

	public enum Service {

		@Schema(description = "정상")
		NORMAL,
		@Schema(description = "지연 중 — 5xx ≥ 2%(actuator · 헬스 체크 제외) · 발급 p95 ≥ 2초 · redis_up = 0 중 하나")
		DELAYED,
		@Schema(description = "확인 중 — 데이터 지연이거나 판정 재료가 없음(redis_up 없음 포함)")
		CHECKING

	}

	/**
	 * endsInSec = 종료(endAt)까지. ENDED면 null. soldOutAt = SOLD_OUT(남은 재고가 0이 된 분) · ENDED(결과 준비 뒤 마지막 발급 성공 분,
	 * 품절이 아니면 null). service는 Prometheus를 읽는 상태에서만
	 */
	public record Header(Long endsInSec, OffsetDateTime soldOutAt, Service service) {
	}

	/**
	 * 핵심 지표. stock · planCapacity · planName은 DB, 나머지는 Prometheus.
	 * issued = stock − 남은 재고(Redis 기준). waitMinutes: null = 모름 · 처리 0 · 품절 · 시작 전, 0 = 1분 이내, 그 밖 반올림한 분
	 */
	public record Kpi(Long issued, Long stock, @Schema(description = "품절 응답 — 입장부터 지금까지(분당 품절의 합)") Long soldOutCount,
			@Schema(description = "대기 인원(추정) = 마지막 번호표 − 서빙 커서. 시작 전 0") Long waiting,
			Integer waitMinutes,
			@Schema(description = "접속 중 — 결과 탭 최대 동시 접속자와 같은 정의(직전 2시간 세션)") Long activeSessions,
			Long planCapacity, String planName, @Schema(description = "입장 ~ 지금 중 최고 동시 접속. 값이 없으면 null") Peak peak) {
	}

	/** at = 처음 그 값이 된 분(결과 탭 peak_at과 같은 분 기준) */
	public record Peak(Long value, OffsetDateTime at) {
	}

	/**
	 * 1분 칸(입장 분 ~ 지금 분). t = 구간 시작(결과 탭 분당 그래프와 같은 분), 마지막 칸은 지금까지.
	 * projection = 소진 예상(시작 5분 뒤부터, 성공 속도 &gt; 0, 품절 전)
	 */
	public record Series(List<MinutePoint> perMinute, List<StockPoint> stock, Projection projection) {
	}

	/** t부터 1분 동안의 입장(신규 세션) · 발급 성공 · 품절 — Pod별 누적값의 차이(정수, 칸을 더하면 누적값) */
	public record MinutePoint(OffsetDateTime t, Long entries, Long success, Long soldOut) {
	}

	/** t 분에 읽은 남은 재고(마지막 칸 = 지금 값) */
	public record StockPoint(OffsetDateTime t, Long remaining) {
	}

	public record Projection(OffsetDateTime at) {
	}

}
