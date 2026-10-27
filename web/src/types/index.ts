/**
 * API 응답 모양 그대로 (Portal API `/api/portal`). 상태 · 타입 값은 DB 값(영문 UPPER_SNAKE_CASE),
 * 일시는 ISO-8601 +09:00 문자열 — 화면에 그릴 때 utils/format 의 formatDateTime 으로 바꾼다.
 */

/** 로그인한 테넌트 관리자 계정 (GET /me) */
export interface Account {
  companyName: string;
  tenantId: string;
  /** 신청자 표시용 담당자 이름 */
  managerName: string;
  email: string;
}

/** 요금제 (GET /plans) — 금액은 화면 · API에 없다 */
export interface Plan {
  /** S · M · L · XL */
  planCode: string;
  /** 최대 동시 수용 인원 */
  maxCapacity: number;
}

/** 이벤트 타입 — 쿠폰 / 타임세일 / 라이브커머스 (UI-TP-07) */
export type EventType = 'COUPON' | 'TIMESALE' | 'LIVE_COMMERCE';

/** 신청 상태 5종 (UI-TP-05) */
export type ApplicationStatus = 'APPLYING' | 'APPLIED' | 'APPLY_FAILED' | 'APPROVED' | 'REJECTED';
/** 호스팅 상태 7종 — 승인 전 null */
export type HostingStatus = 'PENDING' | 'DEPLOYED' | 'DEPLOY_FAILED' | 'HOSTING' | 'HOSTING_FAILED' | 'ENDED' | 'END_FAILED';
/** 이벤트 상태 4종 — 승인 전 null */
export type EventStatus = 'SCHEDULED' | 'OPEN' | 'OPEN_FAILED' | 'EXPIRED';

/** 화면 구성 위젯 위치 (UI-TP-09) — 신청 요청 · 상세 응답의 layout 과 같은 모양 */
export type HAlign = 'LEFT' | 'CENTER' | 'RIGHT';
export type VAlign = 'TOP' | 'MIDDLE' | 'BOTTOM';
export type ScreenWidget = 'title' | 'banner' | 'couponList' | 'issueButton';
export type ScreenTheme = 'WHITE' | 'WARM_ORANGE' | 'DARK' | 'BLUE';

export type ScreenSetting = Record<ScreenWidget, { h: HAlign; v: VAlign }> & { theme: ScreenTheme };

/** 목록의 한 줄 (GET /events) */
export interface EventSummary {
  eventId: number;
  name: string;
  eventType: EventType;
  applicationStatus: ApplicationStatus;
  hostingStatus: HostingStatus | null;
  eventStatus: EventStatus | null;
  startAt: string;
  endAt: string;
  hostingStartAt: string;
  hostingEndAt: string;
  appliedAt: string;
  trafficPlanCode: string;
}

/** 결과 탭 상태 — IN_PROGRESS면 결과 탭을 막는다 */
export type ResultState = 'END_FAILED' | 'READY' | 'AGGREGATING' | 'IN_PROGRESS';

export type StatusType = 'APPLICATION' | 'HOSTING' | 'EVENT';
/** 누가 바꿨나 — TENANT 테넌트 관리자 / OPERATOR 운영자 / SYSTEM 자동 처리 */
export type Actor = 'TENANT' | 'OPERATOR' | 'SYSTEM';

/** 진행 기록 한 건 (신청 · 호스팅 · 이벤트) */
export interface StatusLog {
  statusType: StatusType;
  prevStatus: string | null;
  newStatus: string;
  changedAt: string;
  /** 원인 · 사유 (반려 사유, 실패 원인 등) */
  detail: string | null;
  actor: Actor;
}

export interface Coupon {
  couponId: number;
  name: string;
  /** 화면 노출 문구 */
  description: string;
}

/** 상세 (GET /events/{id}) */
export interface EventDetail {
  eventId: number;
  name: string;
  eventType: EventType;
  startAt: string;
  endAt: string;
  hostingStartAt: string;
  hostingEndAt: string;
  /** 고객사 이벤트 페이지 주소 */
  endpointUrl: string;
  trafficPlanCode: string;
  /** 총 발급 재고 (이벤트 단위) */
  stockCount: number;
  appliedAt: string;
  /** 신청자(테넌트 담당자 이름) */
  applicantName: string;
  status: {
    applicationStatus: ApplicationStatus;
    hostingStatus: HostingStatus | null;
    eventStatus: EventStatus | null;
    /** REJECTED일 때만 */
    rejectReason: string | null;
    resultState: ResultState;
  };
  coupons: Coupon[];
  layout: ScreenSetting;
  /** 승인되고 만료 전일 때만 */
  domain: string | null;
  /** 시각 순서 */
  logs: StatusLog[];
}

/** 결과 (GET /events/{id}/result) — state별로 쓰지 않는 값은 null */
export interface EventResult {
  eventId: number;
  state: ResultState;
  eventOpenAt: string;
  /** READY일 때만 */
  aggregatedAt: string | null;
  hostingStatus: HostingStatus | null;
  eventStatus: EventStatus | null;
  /** IN_PROGRESS일 때만 — 종료 예정 */
  endAt: string | null;
  /** END_FAILED일 때만 */
  failure: { cause: string | null; occurredAt: string } | null;
  /** READY일 때만 */
  result: {
    metrics: {
      successCount: number;
      totalStock: number;
      /** 0~1 */
      selloutRate: number;
      soldoutCount: number;
      peakConcurrent: number | null;
      maxCapacity: number;
      /** 0~1 */
      capacityUsageRate: number | null;
      peakAt: string | null;
    };
    composition: { successCount: number; soldoutCount: number; abandonedCount: number };
    /** 대기열 진입 시각(분) 기준 */
    perMinute: { minute: string; successCount: number; soldoutCount: number }[];
  } | null;
}

/** 내려받기 링크 (CSV · 리포트) — presigned URL, 만료 뒤에는 다시 요청 */
export interface DownloadLink {
  url: string;
  expiresAt: string;
}

/** 알림 우선순위 — 긴급 / 경고 / 정보 */
export type NotificationPriority = 'URGENT' | 'WARNING' | 'INFO';

/** 알림 (GET /notifications) — 최신순 */
export interface PortalNotification {
  notificationId: number;
  /** 관련 이벤트 — 없으면 null */
  eventId: number | null;
  /** 분류 (고도화 때 확정 — 지금은 APPLICATION) */
  category: string;
  priority: NotificationPriority;
  occurredAt: string;
  content: string;
  read: boolean;
}

/** 리포트 (GET /reports) — 최근 생성 먼저 */
export interface Report {
  reportId: number;
  name: string;
  category: string;
  periodStart: string;
  periodEnd: string;
  createdAt: string;
}

/** 알림 설정 (GET /settings) — Slack 연동은 MVP 1차에 없다(slackAvailable false) */
export interface PortalSettings {
  emailEnabled: boolean;
  slackAvailable: boolean;
}

/* ---------- 모니터링 (UI-TP-11) — api MonitoringResponses와 같은 모양 ---------- */

/** BEFORE_ENTRY 입장 전 · ENTRY_OPEN 입장 ~ 시작 · LIVE 진행 중 · SOLD_OUT 품절 · ENDED 종료(hosting_end_at) */
export type MonitoringPhase = 'BEFORE_ENTRY' | 'ENTRY_OPEN' | 'LIVE' | 'SOLD_OUT' | 'ENDED';
export type MonitoringGroup = 'LIVE' | 'TODAY' | 'ENDED';
export type MonitoringStepKey = 'HOSTING_START' | 'ENTRY' | 'LIVE' | 'END' | 'AGGREGATE';
export type MonitoringStepStatus = 'DONE' | 'CURRENT' | 'UPCOMING';
/** 서비스 표시 — 정상 · 지연 중 · 확인 중 */
export type MonitoringService = 'NORMAL' | 'DELAYED' | 'CHECKING';

/** 드롭다운 항목. state는 시각 기준(목록은 SOLD_OUT이 나오지 않는다) */
export interface MonitoringEventItem {
  eventId: number;
  name: string;
  group: MonitoringGroup;
  state: MonitoringPhase;
  prepareFailed: boolean;
  entryAt: string;
  startAt: string;
  hostingEndAt: string;
}

export interface MonitoringEventList {
  events: MonitoringEventItem[];
  /** 진행 중 → 입장 중 → 가장 가까운 예정 → 가장 최근 종료. 목록이 비면 null */
  defaultEventId: number | null;
}

export interface MonitoringStep {
  step: MonitoringStepKey;
  /** AGGREGATE는 null */
  at: string | null;
  status: MonitoringStepStatus;
}

export interface MonitoringKpi {
  issued: number | null;
  stock: number;
  soldOutCount: number | null;
  /** 대기 인원(추정). 시작 전 0 */
  waiting: number | null;
  /** null = 모름 · 처리 0 · 품절 · 시작 전, 0 = 1분 이내 */
  waitMinutes: number | null;
  activeSessions: number | null;
  planCapacity: number | null;
  planName: string;
  peak: { value: number | null; at: string | null } | null;
}

export interface MonitoringSeries {
  /** t까지 1분 동안 (입장 ~ 지금) */
  perMinute: { t: string; entries: number | null; success: number | null; soldOut: number | null }[];
  stock: { t: string; remaining: number | null }[];
  /** 소진 예상 — 시작 5분 뒤부터, 품절 전 */
  projection: { at: string } | null;
}

/** GET /monitoring/events/{id}. kpi · series는 ENTRY_OPEN · LIVE · SOLD_OUT에서만 */
export interface EventMonitoring {
  eventId: number;
  name: string;
  state: MonitoringPhase;
  /** 준비(WF2-2) 실패 — 입장 전처럼 보이고 안내만 */
  prepareFailed: boolean;
  /** 데이터 지연 — 값은 마지막 정상 값 */
  stale: boolean;
  lastUpdatedAt: string | null;
  /** 남은 시간 계산 기준(브라우저 시계를 쓰지 않는다) */
  serverTime: string;
  period: { hostingStartAt: string; entryAt: string; startAt: string; endAt: string; hostingEndAt: string; domain: string | null };
  steps: MonitoringStep[];
  header: { endsInSec: number | null; soldOutAt: string | null; service: MonitoringService | null };
  kpi: MonitoringKpi | null;
  series: MonitoringSeries | null;
  rejects5m: number | null;
}
