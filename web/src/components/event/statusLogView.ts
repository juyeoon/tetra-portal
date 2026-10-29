import type { StatusLog, StatusType } from '@/types';
import { entryOpenAtOf } from '../../utils/event.ts';
import { formatDateTime, formatMonthDayTime } from '../../utils/format.ts';
import { CHECKING_NOTE, actorLabel, eventStatusMeta, statusMetaOf, statusTypeLabel, type StatusMeta } from '../../utils/status.ts';

/**
 * 상태 처리 기록 → 화면 모델 (화면과 분리, node --test 대상). Console eventView.ts의 statusLogView · eventStartView · timelineOf와 같은 규칙.
 * 이 모듈과 이 모듈이 부르는 파일은 상대 경로 + .ts로 import한다(Node가 @/ 를 풀지 못함).
 */

/**
 * 상태 로그 단계 이름 · 설명 — 서버는 상태 전이만 주므로 문구는 화면이 정한다(Console STATUS_LOG_TEXT와 같음).
 * 실패(배포 · 호스팅 시작 · 이벤트 준비 · 종료)만 다르다 — 고객 화면은 "확인 중"(Console은 실패 문구, 2026-10-29).
 * 원인 · 반려 사유(detail)는 설명과 따로 보여 준다. 호스팅 · 이벤트 기록의 원인은 서버가 내리지 않는다.
 */
export const STATUS_LOG_TEXT: Record<StatusType, Record<string, { title: string; description: string }>> = {
  APPLICATION: {
    APPLYING: { title: '신청 접수', description: '테넌트가 이벤트 신청서를 제출했습니다. 신청 정보와 배너 이미지 저장을 시작합니다.' },
    APPLIED: { title: '신청 저장 완료', description: '신청 정보 저장 및 배너 업로드가 완료되어 승인 대기 상태로 전환되었습니다.' },
    APPLY_FAILED: { title: '신청 실패', description: '신청 정보를 저장하지 못했습니다.' },
    APPROVED: { title: '운영자 승인', description: '운영자가 신청을 승인했습니다. 할당 도메인과 인증키가 발급되었습니다.' },
    REJECTED: { title: '운영자 반려', description: '운영자가 신청을 반려했습니다.' },
  },
  HOSTING: {
    PENDING: { title: '배포 시작', description: '승인과 함께 이벤트 페이지 배포를 시작했습니다.' },
    DEPLOYED: { title: '배포 완료', description: '이벤트 페이지와 리소스 배포가 끝났습니다.' },
    DEPLOY_FAILED: { title: '배포 확인 중', description: CHECKING_NOTE },
    // 입장 시각("입장은 MM.DD HH:mm에 열립니다.")은 statusLogView가 이벤트 시작 시각으로 붙인다
    HOSTING: { title: '호스팅 시작', description: '호스팅 시작 시각이 되어 이벤트 페이지(대기방)를 열었습니다.' },
    HOSTING_FAILED: { title: '호스팅 시작 확인 중', description: CHECKING_NOTE },
    ENDED: { title: '호스팅 종료', description: '호스팅 기간이 끝나 이벤트 페이지를 내렸습니다.' },
    END_FAILED: { title: '종료 처리 확인 중', description: CHECKING_NOTE },
  },
  EVENT: {
    SCHEDULED: { title: '오픈 예약', description: '승인되어 이벤트 일정에 맞춰 오픈이 예약되었습니다.' },
    // OPEN은 WF2-2가 끝나는 입장 시각(시작 − ENTRY_OPEN_LEAD_MINUTES)에 된다 — 입장(대기열)만 열리고 발급은 시작 시각부터
    OPEN: { title: '입장 시작', description: '입장이 열려 대기열을 받고 있습니다. 쿠폰 발급은 이벤트 시작 시각부터입니다.' },
    OPEN_FAILED: { title: '입장 준비 확인 중', description: CHECKING_NOTE },
    EXPIRED: { title: '이벤트 만료', description: '이벤트가 종료되어 발급이 마감되었습니다.' },
  },
};

/** 상태 처리 기록 한 칸 */
export interface StatusLogItem {
  key: string;
  /** 표시용 'YYYY.MM.DD HH:mm' */
  at: string;
  actor: string;
  /** 신청 / 호스팅 / 이벤트 */
  typeLabel: string;
  title: string;
  status: StatusMeta;
  description: string;
  /** 반려 사유 · 원인. 없으면 null */
  detail: { label: string; text: string; rejected: boolean } | null;
  /** 아직 오지 않은 일정(이벤트 시작 전) — 회색 */
  planned: boolean;
}

/** @param startAt 이벤트 시작 시각. 호스팅 시작 설명에 입장 시각을 붙인다 */
export function statusLogView(log: StatusLog, startAt: string, applicantName: string): StatusLogItem {
  const meta = statusMetaOf(log.statusType, log.newStatus);
  const text = STATUS_LOG_TEXT[log.statusType]?.[log.newStatus] ?? { title: meta.label, description: '' };
  const rejected = log.statusType === 'APPLICATION' && log.newStatus === 'REJECTED';
  const hostingStarted = log.statusType === 'HOSTING' && log.newStatus === 'HOSTING';
  return {
    key: `${log.statusType}-${log.newStatus}-${log.changedAt}`,
    at: formatDateTime(log.changedAt),
    actor: actorLabel(log.actor, applicantName),
    typeLabel: statusTypeLabel[log.statusType] ?? log.statusType,
    title: text.title,
    status: meta,
    description: hostingStarted ? `${text.description} 입장은 ${formatMonthDayTime(entryOpenAtOf(startAt))}에 열립니다.` : text.description,
    detail: log.detail ? { label: rejected ? '반려 사유' : '원인', text: log.detail, rejected } : null,
    planned: false,
  };
}

/**
 * "이벤트 시작" — 상태 전이가 아니라 이벤트 시작 시각(start_at)으로 화면이 만드는 항목.
 * 시작 전이면 예정(회색 · "예정" 칩), 지나면 진행 중 + 발급 시작 문구.
 */
export function eventStartView(startAt: string, now: Date): StatusLogItem {
  const planned = now.getTime() < new Date(startAt).getTime();
  return {
    key: `EVENT-START-${startAt}`,
    at: formatDateTime(startAt),
    actor: planned ? '예정' : '자동 처리',
    typeLabel: statusTypeLabel.EVENT,
    title: '이벤트 시작',
    status: planned ? { label: '예정', tone: 'grey' } : eventStatusMeta.OPEN,
    description: planned ? '이벤트 시작 시각에 쿠폰 발급이 시작됩니다.' : '쿠폰 발급이 시작되었습니다.',
    detail: null,
    planned,
  };
}

/**
 * 상태 로그 + "이벤트 시작"을 시각순으로. 입장이 열린 적(EVENT OPEN 기록)이 있을 때만 이벤트 시작을 넣는다 —
 * 반려 · 배포 · 호스팅 시작 · 입장 준비가 끝나지 않은 이벤트(확인 중) · 입장 전 취소는 OPEN 기록이 없어 빠진다.
 * 같은 시각이면 로그 뒤에 둔다(안정 정렬).
 */
export function timelineOf(logs: StatusLog[], startAt: string, now: Date, applicantName: string): StatusLogItem[] {
  const entries = logs.map((log) => ({ at: new Date(log.changedAt).getTime(), item: statusLogView(log, startAt, applicantName) }));
  if (logs.some((l) => l.statusType === 'EVENT' && l.newStatus === 'OPEN')) {
    entries.push({ at: new Date(startAt).getTime(), item: eventStartView(startAt, now) });
  }
  return entries.sort((a, b) => a.at - b.at).map((e) => e.item);
}
