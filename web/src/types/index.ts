/** 트래픽 수용 인원 플랜 — S 1만 / M 3만 / L 5만 / XL 10만 (UI-TP-08) */
export type Plan = 'S' | 'M' | 'L' | 'XL';

/** 로그인한 테넌트 관리자 계정 */
export interface Account {
  companyName: string;
  tenantId: string;
  /** 신청자 표시용 담당자 이름 */
  managerName: string;
  email: string;
}

/** 이벤트 타입 — 쿠폰 / 타임세일 / 라이브커머스 (UI-TP-07) */
export type EventType = 'COUPON' | 'TIME_SALE' | 'LIVE_COMMERCE';

/** 신청상태 5종 — 신청됨(처리 중) / 승인 대기 중 / 신청 실패 / 승인됨 / 반려됨 (UI-TP-05) */
export type ApplicationStatus = 'APPLYING' | 'APPLIED' | 'APPLY_FAILED' | 'APPROVED' | 'REJECTED';
/** 호스팅상태 2종 — 호스팅 중 / 호스팅 종료 */
export type HostingStatus = 'HOSTING' | 'ENDED';
/** 이벤트상태 2종 — 진행 중 / 만료 */
export type EventStatus = 'OPEN' | 'EXPIRED';

export interface Coupon {
  name: string;
  /** 화면 노출 문구 */
  description: string;
  quantity: number;
}

export interface BannerFile {
  name: string;
  width: number;
  height: number;
}

/** 화면 구성 위젯 위치 (UI-TP-09) */
export type HAlign = 'LEFT' | 'CENTER' | 'RIGHT';
export type VAlign = 'TOP' | 'MIDDLE' | 'BOTTOM';
export type ScreenWidget = 'title' | 'banner' | 'couponList' | 'issueButton';
export type ScreenTheme = 'WHITE' | 'WARM_ORANGE' | 'DARK' | 'BLUE';

export interface ScreenSetting {
  widgets: Record<ScreenWidget, { h: HAlign; v: VAlign }>;
  theme: ScreenTheme;
}

/** 상태 처리 기록 (Status Log) */
export interface StatusLog {
  status: ApplicationStatus;
  at: string;
  /** 처리 내용 (예: '다란 (테넌트 관리자)', '플랫폼 운영자') */
  by: string;
  /** 반려 사유 — 운영자가 OPS-03-M2에서 입력 */
  reason?: string;
}

/** 이벤트 결과 (UI-TP-6-2) */
export interface EventResult {
  openedAt: string;
  aggregatedAt: string;
  issued: number;
  /** 재고 소진 후 발급 시도 */
  soldOut: number;
  /** 대기열 이탈 */
  dropped: number;
  peakConcurrent: number;
  peakAt: string;
  /** 오픈 후 10분(00:00~00:09) 분당 발급 결과 */
  perMinute: { minute: string; issued: number; soldOut: number }[];
}

export interface PortalEvent {
  /** {테넌트ID}-evt-{번호}-01 */
  id: string;
  name: string;
  type: EventType;
  eventStart: string;
  eventEnd: string;
  hostingStart: string;
  hostingEnd: string;
  /** 승인 후 발급 */
  domain?: string;
  banner: BannerFile;
  plan: Plan;
  /** 선택 규모 초과 시 추가요금(extracharge) 산정 포함 여부 */
  extraTraffic: boolean;
  endpointUrl: string;
  coupons: Coupon[];
  screen: ScreenSetting;
  appliedAt: string;
  applicant: string;
  applicationStatus: ApplicationStatus;
  /** 승인 전에는 없음('-' 표시) */
  hostingStatus?: HostingStatus;
  eventStatus?: EventStatus;
  logs: StatusLog[];
  result?: EventResult;
}

/** 알림 우선순위 — 긴급 / 경고 / 정보 */
export type NotificationPriority = 'CRITICAL' | 'WARNING' | 'INFO';

export interface PortalNotification {
  id: number;
  priority: NotificationPriority;
  message: string;
  at: string;
  read: boolean;
}

export interface Report {
  id: number;
  name: string;
  period: string;
  createdAt: string;
}

export interface SlackSetting {
  connected: boolean;
  /** 연동된 워크스페이스·계정 표시 */
  connectedLabel?: string;
  /** 워크스페이스 등록 (최대 1개) */
  workspace: string;
  channels: Record<NotificationPriority, string>;
}

export interface PortalSettings {
  emailEnabled: boolean;
  slack: SlackSetting;
}
