import type { Tone } from '@/theme/theme';
import type { Actor, ApplicationStatus, EventStatus, EventType, HostingStatus, NotificationPriority, StatusType } from '@/types';
import { entryOpenAtOf } from './event.ts';

export interface StatusMeta {
  label: string;
  tone: Tone;
  /** 영문 병기 (상세 상단·상태 처리 기록) */
  en?: string;
}

/**
 * 고객 화면에서 실패(배포 · 호스팅 시작 · 이벤트 준비 · 종료)와 멈춘 상태를 대신하는 표시 — 실패 · 원인은 보여 주지 않는다(2026-10-29).
 * 콘솔은 실패 문구를 그대로 보여 준다(운영자가 처리해야 하므로).
 */
export const CHECKING: StatusMeta = { label: '확인 중', tone: 'amber' };
export const CHECKING_NOTE = '운영팀이 확인하고 있습니다.';

/** 신청 상태 5종 (UI-TP-05) — 콘솔과 같은 톤 */
export const applicationStatusMeta: Record<ApplicationStatus, StatusMeta> = {
  APPLYING: { label: '신청됨(처리 중)', tone: 'amber', en: 'Processing' },
  APPLIED: { label: '승인 대기 중', tone: 'blue', en: 'Pending' },
  APPLY_FAILED: { label: '신청 실패', tone: 'grey', en: 'Failed' },
  APPROVED: { label: '승인됨', tone: 'green', en: 'Approved' },
  REJECTED: { label: '반려됨', tone: 'red', en: 'Rejected' },
};

/** 호스팅 상태 7종 — 콘솔과 같은 문구 · 톤, 실패 3종만 확인 중 */
export const hostingStatusMeta: Record<HostingStatus, StatusMeta> = {
  PENDING: { label: '배포 대기', tone: 'amber' },
  DEPLOYED: { label: '배포 완료', tone: 'blue' },
  DEPLOY_FAILED: CHECKING,
  HOSTING: { label: '호스팅 중', tone: 'green' },
  HOSTING_FAILED: CHECKING,
  ENDED: { label: '호스팅 종료', tone: 'grey' },
  END_FAILED: CHECKING,
};

/** 이벤트 상태 4종 — 콘솔과 같은 문구 · 톤, 준비 실패만 확인 중 */
export const eventStatusMeta: Record<EventStatus, StatusMeta> = {
  SCHEDULED: { label: '오픈 예정', tone: 'blue' },
  OPEN: { label: '진행 중', tone: 'green' },
  OPEN_FAILED: CHECKING,
  EXPIRED: { label: '만료', tone: 'grey' },
};

const msOf = (iso: string) => new Date(iso).getTime();

/** 실패한 호스팅 — 배포가 실패하면 이후 흐름(WF2 · WF4)이 돌지 않아 이벤트 상태가 오픈 예정으로 남는다 */
const FAILED_HOSTING: ReadonlySet<HostingStatus> = new Set<HostingStatus>(['DEPLOY_FAILED', 'HOSTING_FAILED', 'END_FAILED']);

/**
 * 호스팅 칸. 실패면 확인 중, 배포 대기 · 배포 완료인 채 호스팅 시작 시각이 지났어도 확인 중
 * (모니터링 호스팅 시작 칸과 같은 기준). 승인 전(null)이면 null.
 */
export function hostingStatusView(hosting: HostingStatus | null, hostingStartAt: string, nowMs: number): StatusMeta | null {
  if (hosting == null) return null;
  if ((hosting === 'PENDING' || hosting === 'DEPLOYED') && nowMs >= msOf(hostingStartAt)) return CHECKING;
  return hostingStatusMeta[hosting];
}

/**
 * 이벤트 칸. 호스팅이 실패했거나 이벤트 준비가 실패했으면 이벤트 상태와 상관없이 확인 중.
 * 실패 기록이 없어도 오픈 예정인 채 입장 시각(시작 − ENTRY_OPEN_LEAD_MINUTES)이 지났으면 확인 중(모니터링 입장 칸과 같은 기준).
 * 승인 전(null)이면 null.
 */
export function eventStatusView(hosting: HostingStatus | null, event: EventStatus | null, startAt: string, nowMs: number): StatusMeta | null {
  if (event == null) return null;
  if (event === 'OPEN_FAILED' || (hosting != null && FAILED_HOSTING.has(hosting))) return CHECKING;
  if (event === 'SCHEDULED' && nowMs >= msOf(entryOpenAtOf(startAt))) return CHECKING;
  return eventStatusMeta[event];
}

export const isChecking = (meta: StatusMeta | null) => meta?.label === CHECKING.label;

export interface EventStatusChips {
  hosting: StatusMeta | null;
  event: StatusMeta | null;
  /** 둘 중 하나라도 확인 중 — 안내 한 줄 · 결과 탭 툴팁 · 도메인 안내 */
  checking: boolean;
}

/** 목록 · 상세 상단의 호스팅 · 이벤트 칸 */
export function eventChipsOf(
  s: { hostingStatus: HostingStatus | null; eventStatus: EventStatus | null },
  period: { startAt: string; hostingStartAt: string },
  nowMs: number,
): EventStatusChips {
  const hosting = hostingStatusView(s.hostingStatus, period.hostingStartAt, nowMs);
  const event = eventStatusView(s.hostingStatus, s.eventStatus, period.startAt, nowMs);
  return { hosting, event, checking: isChecking(hosting) || isChecking(event) };
}

/** 진행 기록의 상태 값 → 표시 (statusType별로 다른 표) */
export function statusMetaOf(statusType: StatusType, value: string): StatusMeta {
  const table: Record<string, StatusMeta> =
    statusType === 'APPLICATION' ? applicationStatusMeta : statusType === 'HOSTING' ? hostingStatusMeta : eventStatusMeta;
  return table[value] ?? { label: value, tone: 'grey' };
}

export const statusTypeLabel: Record<StatusType, string> = {
  APPLICATION: '신청',
  HOSTING: '호스팅',
  EVENT: '이벤트',
};

/** 진행 기록의 처리 주체 문구 */
export function actorLabel(actor: Actor, applicantName: string) {
  if (actor === 'TENANT') return `${applicantName} (테넌트 관리자)`;
  if (actor === 'OPERATOR') return '플랫폼 운영자';
  return '자동 처리';
}

export const eventTypeLabel: Record<EventType, string> = {
  COUPON: '쿠폰',
  TIMESALE: '타임세일',
  LIVE_COMMERCE: '라이브커머스',
};

/** 알림 우선순위 태그 — 긴급(빨강) / 주의(주황) / 정보(파랑) */
export const priorityMeta: Record<NotificationPriority, StatusMeta> = {
  URGENT: { label: '긴급', tone: 'red', en: 'Urgent' },
  WARNING: { label: '주의', tone: 'amber', en: 'Warning' },
  INFO: { label: '정보', tone: 'blue', en: 'Info' },
};
