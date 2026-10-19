import type { Tone } from '@/theme/theme';
import type { Actor, ApplicationStatus, EventStatus, EventType, HostingStatus, NotificationPriority, StatusType } from '@/types';

export interface StatusMeta {
  label: string;
  tone: Tone;
  /** 영문 병기 (상세 상단·상태 처리 기록) */
  en?: string;
}

/** 신청 상태 5종 (UI-TP-05) — 콘솔과 같은 톤 */
export const applicationStatusMeta: Record<ApplicationStatus, StatusMeta> = {
  APPLYING: { label: '신청됨(처리 중)', tone: 'amber', en: 'Processing' },
  APPLIED: { label: '승인 대기 중', tone: 'blue', en: 'Pending' },
  APPLY_FAILED: { label: '신청 실패', tone: 'grey', en: 'Failed' },
  APPROVED: { label: '승인됨', tone: 'green', en: 'Approved' },
  REJECTED: { label: '반려됨', tone: 'red', en: 'Rejected' },
};

/** 호스팅 상태 7종 — 콘솔과 같은 문구 · 톤 */
export const hostingStatusMeta: Record<HostingStatus, StatusMeta> = {
  PENDING: { label: '배포 대기', tone: 'amber' },
  DEPLOYED: { label: '배포 완료', tone: 'blue' },
  DEPLOY_FAILED: { label: '배포 실패', tone: 'red' },
  HOSTING: { label: '호스팅 중', tone: 'green' },
  HOSTING_FAILED: { label: '호스팅 준비 실패', tone: 'red' },
  ENDED: { label: '호스팅 종료', tone: 'grey' },
  END_FAILED: { label: '종료 실패', tone: 'red' },
};

/** 이벤트 상태 4종 — 콘솔과 같은 문구 · 톤 */
export const eventStatusMeta: Record<EventStatus, StatusMeta> = {
  SCHEDULED: { label: '오픈 예정', tone: 'blue' },
  OPEN: { label: '진행 중', tone: 'green' },
  OPEN_FAILED: { label: '이벤트 준비 실패', tone: 'red' },
  EXPIRED: { label: '만료', tone: 'grey' },
};

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

/** 알림 우선순위 태그 — 긴급(빨강) / 경고(주황) / 정보(파랑) */
export const priorityMeta: Record<NotificationPriority, StatusMeta> = {
  URGENT: { label: '긴급', tone: 'red', en: 'Urgent' },
  WARNING: { label: '경고', tone: 'amber', en: 'Warning' },
  INFO: { label: '정보', tone: 'blue', en: 'Info' },
};
