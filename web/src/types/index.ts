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
