import type { PortalNotification, Report } from '@/types';

/** 알림 센터 (UI-TP-13) — 최신순 */
export const mockNotifications: PortalNotification[] = [
  { id: 6, priority: 'INFO', message: 'EVT-1042 이벤트가 승인되었습니다.', at: '2026.09.17 14:02', read: false },
  { id: 5, priority: 'INFO', message: 'EVT-1046 이벤트 신청이 접수되었습니다.', at: '2026.09.15 10:30', read: false },
  { id: 4, priority: 'CRITICAL', message: 'EVT-1035 이벤트가 반려되었습니다. (사유: 목표 동시 접속 규모 초과)', at: '2026.09.05 09:12', read: true },
  { id: 3, priority: 'INFO', message: '8월 감사 로그 리포트가 생성되었습니다.', at: '2026.09.01 00:05', read: true },
  { id: 2, priority: 'WARNING', message: '시스템 점검 안내: 09/25 02:00~04:00', at: '2026.08.28 17:40', read: true },
  { id: 1, priority: 'INFO', message: '새로운 템플릿 샘플이 추가되었습니다.', at: '2026.08.20 11:00', read: true },
];

/** 리포트 (UI-TP-12) — 매월 자동 생성 */
export const mockReports: Report[] = [
  { id: 3, name: '9월 감사 로그', period: '2026.09.01 ~ 09.30', createdAt: '2026.10.01' },
  { id: 2, name: 'EVT-1041 이벤트 처리 로그', period: '2026.09.10 ~ 09.15', createdAt: '2026.09.16' },
  { id: 1, name: '8월 감사 로그', period: '2026.08.01 ~ 08.31', createdAt: '2026.09.01' },
];
