package com.tetra.portal.monitoring;

import com.tetra.portal.tenancy.TenantIds;

/**
 * 모니터링 PromQL(명세 3절). 쿼리는 이 클래스로만 만든다.
 * 모든 식에 tenant_id(로그인 JWT · TenantIds.requireValid)와 event_id(DB에서 소유를 확인한 양의 정수)를 함께 건다 — 요청 경로 값으로 만들지 않는다.
 * 게이지(tetra_event_*, redis_up)는 모든 Pod가 같은 값을 내므로 max, 카운터는 Pod별이라 sum.
 * 라벨: 앱 지표는 Micrometer 공통 태그, redis_up은 PodMonitor relabel(infra-k8s charts/event redis.yaml)로 둘 다 붙는다.
 */
final class MonitoringQueries {

	/** 발급(claim) 경로 — http_server_requests uri 라벨 값 그대로(정확히 일치라 정규식 이스케이프 없음) */
	static final String CLAIM_URI = "/api/issuance/events/{eventId}/coupons/claim";

	/** 서비스 판정에서 뺄 uri — 수집(actuator)과 헬스 체크. 실제 고객 요청만 본다 */
	static final String EXCLUDED_URIS = "/actuator.*|/api/issuance/health";

	private final String sel;

	MonitoringQueries(String tenantId, long eventId) {
		if (eventId <= 0) {
			throw new IllegalArgumentException("event_id가 양수가 아닙니다.");
		}
		this.sel = "tenant_id=\"%s\",event_id=\"%d\"".formatted(TenantIds.requireValid(tenantId), eventId);
	}

	// ---- 게이지 (max) ----

	String stockRemaining() {
		return "max(tetra_event_stock_remaining{%s})".formatted(sel);
	}

	/** 대기 인원 ≈ 마지막 번호표 − 서빙 커서 (0 미만은 MonitoringRules가 0으로) */
	String waiting() {
		return "max(tetra_event_ticket_last{%s}) - max(tetra_event_cursor{%s})".formatted(sel, sel);
	}

	/** 접속 중 — 결과 탭 최대 동시 접속자와 같은 정의(직전 세션 TTL 창) */
	String sessionsActive() {
		return "max(tetra_event_sessions_active{%s})".formatted(sel);
	}

	String redisUp() {
		return "max(redis_up{%s})".formatted(sel);
	}

	// ---- 카운터 (sum) ----

	/** 최근 1분 처리 수(성공 + 품절) */
	String processedLastMinute() {
		return "sum(increase(tetra_claims_total{%s,replay=\"false\"}[1m]))".formatted(sel);
	}

	/** 최근 5분 성공 속도(건/초) — 소진 예상 */
	String successRate5m() {
		return "sum(rate(tetra_claims_total{%s,result=\"success\",replay=\"false\"}[5m]))".formatted(sel);
	}

	/** 입장 실패(JWT 검증 실패 · jti 재사용) 최근 5분, 사유는 합계만 */
	String rejects5m() {
		return "sum(increase(tetra_session_rejects_total{%s}[5m]))".formatted(sel);
	}

	// ---- 서비스 판정 (최근 1분) ----

	String rps() {
		return "sum(rate(http_server_requests_seconds_count{%s,uri!~\"%s\"}[1m]))".formatted(sel, EXCLUDED_URIS);
	}

	String errorRps() {
		return "sum(rate(http_server_requests_seconds_count{%s,uri!~\"%s\",status=~\"5..\"}[1m]))".formatted(sel,
				EXCLUDED_URIS);
	}

	/** 발급 p95(초) */
	String claimP95() {
		return "histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{%s,uri=\"%s\"}[1m])))"
			.formatted(sel, CLAIM_URI);
	}

	// ---- 누적 카운터 원값(Pod별) — 범위(step 1m)와 지금 값으로 받아 분당 증가분 · 품절 응답 누적을 서버가 만든다 ----
	// increase(...[1m])는 창 안 증가분을 외삽하고(수집 15초면 약 1.33배) 창 사이를 빠뜨려 막대 합이 확정값과 달랐다(이벤트 12).
	// Pod별 직전 값과의 차이는 PromValues.perMinute. 남은 재고 추이 · 최고 동시 접속은 stockRemaining() · sessionsActive()를 범위로

	/** 발급 결과 누적 — result = success · sold_out */
	String claimsByPod() {
		return "sum by (pod, result) (tetra_claims_total{%s,replay=\"false\"})".formatted(sel);
	}

	/** 입장(신규 세션) 누적 */
	String entriesByPod() {
		return "sum by (pod) (tetra_sessions_total{%s,result=\"created\"})".formatted(sel);
	}

}
