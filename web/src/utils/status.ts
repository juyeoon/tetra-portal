import type { Tone } from '@/theme/theme';
import type { ApplicationStatus, EventStatus, EventType, HostingStatus, NotificationPriority } from '@/types';

export interface StatusMeta {
  label: string;
  tone: Tone;
  /** 영문 병기 (상세 상단·상태 처리 기록) */
  en?: string;
}

/** 신청상태 5종 (UI-TP-05) — 콘솔과 같은 톤 */
export const applicationStatusMeta: Record<ApplicationStatus, StatusMeta> = {
  APPLYING: { label: '신청됨(처리 중)', tone: 'amber', en: 'Processing' },
  APPLIED: { label: '승인 대기 중', tone: 'blue', en: 'Pending' },
  APPLY_FAILED: { label: '신청 실패', tone: 'grey', en: 'Failed' },
  APPROVED: { label: '승인됨', tone: 'green', en: 'Approved' },
  REJECTED: { label: '반려됨', tone: 'red', en: 'Rejected' },
};

/** 호스팅상태 2종 */
export const hostingStatusMeta: Record<HostingStatus, StatusMeta> = {
  HOSTING: { label: '호스팅 중', tone: 'green' },
  ENDED: { label: '호스팅 종료', tone: 'grey' },
};

/** 이벤트상태 2종 */
export const eventStatusMeta: Record<EventStatus, StatusMeta> = {
  OPEN: { label: '진행 중', tone: 'green' },
  EXPIRED: { label: '만료', tone: 'grey' },
};

export const eventTypeLabel: Record<EventType, string> = {
  COUPON: '쿠폰',
  TIME_SALE: '타임세일',
  LIVE_COMMERCE: '라이브커머스',
};

/** 알림 우선순위 태그 — 긴급(빨강) / 경고(주황) / 정보(파랑) */
export const priorityMeta: Record<NotificationPriority, StatusMeta> = {
  CRITICAL: { label: '긴급', tone: 'red', en: 'Critical' },
  WARNING: { label: '경고', tone: 'amber', en: 'Warning' },
  INFO: { label: '정보', tone: 'blue', en: 'Info' },
};
