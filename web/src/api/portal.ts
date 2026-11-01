import type { DownloadLink, EventDetail, EventMonitoring, EventResult, EventSummary, EventType, MonitoringEventList, NotificationPage, Plan, PortalSettings, Report, ScreenSetting } from '@/types';
import { api } from './client';

/* ---------- 요금제 · 이벤트 (UI-TP-05 ~ 10) ---------- */

/** 노출된 요금제, 작은 것부터. 금액은 없다 */
export const listPlans = () => api<Plan[]>('GET', '/plans');

export const listEvents = () => api<EventSummary[]>('GET', '/events');

/** 다른 테넌트 · 없는 이벤트는 404 EVENT_NOT_FOUND */
export const getEvent = (eventId: number) => api<EventDetail>('GET', `/events/${eventId}`);

/** 신청 내용 (multipart 의 application 파트). 일시는 ISO +09:00 */
export interface ApplyApplication {
  name: string;
  eventType: EventType;
  startAt: string;
  endAt: string;
  hostingStartAt: string;
  hostingEndAt: string;
  trafficPlanCode: string;
  /** 총 발급 재고 1 ~ 999,999,999 */
  stockCount: number;
  endpointUrl: string;
  /** COUPON이면 1~10개(입력 순서 = 노출 순서), 그 밖의 타입은 빈 목록 */
  coupons: { name: string; description: string }[];
  layout: ScreenSetting;
}

/**
 * 이벤트 신청 — 검사를 통과하면 202 APPLYING. 배너는 그 뒤에 올라가고 결과는 상세의 신청 상태(APPLIED / APPLY_FAILED)로 본다.
 * 입력 오류는 400 INVALID_INPUT + fields(키는 ApplyApplication 항목 이름, 배너는 banner)
 */
export function applyEvent(application: ApplyApplication, banner: File) {
  const form = new FormData();
  form.append('application', new Blob([JSON.stringify(application)], { type: 'application/json' }));
  form.append('banner', banner);
  return api<{ eventId: number; applicationStatus: 'APPLYING' }>('POST', '/events', { form });
}

/* ---------- 결과 (UI-TP-6-2) ---------- */

export const getResult = (eventId: number) => api<EventResult>('GET', `/events/${eventId}/result`);

/** 발급 이력 CSV 링크 — READY가 아니면 409 */
export const getHistoryCsv = (eventId: number) => api<DownloadLink>('GET', `/events/${eventId}/result/history-csv`);

/* ---------- 모니터링 (UI-TP-11) ---------- */

/** 드롭다운 — 진행 중 → 오늘 예정 → 최근 7일 종료 */
export const listMonitoringEvents = () => api<MonitoringEventList>('GET', '/monitoring/events');

/** 이벤트 현황(15초 근사값). 승인 전 · 다른 테넌트 이벤트는 404 EVENT_NOT_FOUND */
export const getEventMonitoring = (eventId: number) => api<EventMonitoring>('GET', `/monitoring/events/${eventId}`);

/* ---------- 알림 · 리포트 · 설정 (UI-TP-12 ~ 14) ---------- */

/** 조건에 맞는 한 페이지와 화면 건수 — 쿼리는 notificationView.listQuery로 만든다 */
export const listNotifications = (query: URLSearchParams) => api<NotificationPage>('GET', `/notifications?${query}`);

export const markNotificationRead = (notificationId: number) => api<void>('PATCH', `/notifications/${notificationId}/read`);

/** 사이드바 배지 — 목록 응답 unreadCount와 같은 값 */
export const getUnreadNotificationCount = () => api<{ count: number }>('GET', '/notifications/unread-count');

/** 그 테넌트의 안 읽은 알림 전부 읽음(지금 페이지만이 아님). 안 읽은 알림이 없어도 204 */
export const markAllNotificationsRead = () => api<void>('PATCH', '/notifications/read-all');

export const listReports = () => api<Report[]>('GET', '/reports');

export const getReportDownload = (reportId: number) => api<DownloadLink>('GET', `/reports/${reportId}/download`);

/** 로그인 계정 이메일로 안내 메일. 발송 실패는 503 MAIL_SEND_FAILED */
export const sendReportEmail = (reportId: number) => api<void>('POST', `/reports/${reportId}/email`);

export const getSettings = () => api<PortalSettings>('GET', '/settings');

export const setEmailEnabled = (enabled: boolean) => api<PortalSettings>('PATCH', '/settings/email', { json: { enabled } });
