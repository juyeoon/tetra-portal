import type { NotificationChip, NotificationPage, NotificationPriority, PortalNotification } from '@/types';

/**
 * UI-TP-13 알림센터 화면 규칙 — 조건 · 주소 쿼리 · 목록 요청 · 빈 목록 · 이동 · 읽음 처리(화면과 분리, node --test 대상).
 * 거르기(우선순위 AND 분류 AND 안 읽은 것만 AND 내용 검색) · 정렬 · 페이지 · 건수는 서버가 한다.
 * 화면은 조건과 페이지를 주소 쿼리에 둔다(새로고침 · 뒤로 가기에도 그대로).
 * 이 모듈과 이 모듈이 부르는 파일은 상대 경로 + .ts로 import한다(Node가 @/ 를 풀지 못함).
 */

export type PriorityFilter = 'ALL' | NotificationPriority;

/** 우선순위 세그먼트 — 이름은 우선순위 태그(priorityMeta)와 같다 */
export const PRIORITY_FILTERS: { key: PriorityFilter; label: string }[] = [
  { key: 'ALL', label: '전체' },
  { key: 'URGENT', label: '긴급' },
  { key: 'WARNING', label: '주의' },
  { key: 'INFO', label: '정보' },
];

export type ChipFilter = 'ALL' | NotificationChip;

/** 칩 이름 — 콘솔 칩(신청 · 이벤트 · 테넌트 · 리포트 · 시스템) 중 Portal에 보이는 것, 같은 순서 */
export const CHIP_LABEL: Record<NotificationChip, string> = {
  APPLICATION: '신청',
  EVENT: '이벤트',
  REPORT: '리포트',
};

/** 분류 드롭다운 메뉴 — 전체 + 3종. 칩이 없는 알림은 '전체'에서만 보인다 */
export const NOTIFICATION_CHIPS: { key: ChipFilter; label: string }[] = [
  { key: 'ALL', label: '전체' },
  ...(Object.keys(CHIP_LABEL) as NotificationChip[]).map((key) => ({ key, label: CHIP_LABEL[key] })),
];

export interface ChipMenuItem {
  key: ChipFilter;
  label: string;
  count: number;
  /** 0건 — 흐리게만, 고를 수는 있다 */
  dim: boolean;
}

/** 분류 메뉴 항목 — 이름 · 서버 건수(지금 우선순위 · 안 읽은 것만 기준) */
export function chipMenuItems(counts: Record<ChipFilter, number>): ChipMenuItem[] {
  return NOTIFICATION_CHIPS.map((c) => ({ ...c, count: counts[c.key], dim: counts[c.key] === 0 }));
}

/** 분류 버튼 글자 — '분류: 전체', 고르면 '분류: 이벤트' */
export function chipButtonLabel(chip: ChipFilter): string {
  return `분류: ${chip === 'ALL' ? '전체' : CHIP_LABEL[chip]}`;
}

/** 한 페이지 건수(서버 기본값과 같다) */
export const PAGE_SIZE = 10;

/** 서버가 받는 페이지 상한 · 검색어 상한(앞뒤 공백을 지운 뒤) */
export const PAGE_MAX = 10_000;
export const QUERY_MAX = 100;

/** 검색어를 조건에 반영하기까지 기다리는 시간 — 입력(한글 조합) 중 요청이 몰리지 않게 */
export const SEARCH_DELAY_MS = 300;

/** 화면에서 고른 조건 */
export interface NotificationFilters {
  priority: PriorityFilter;
  chip: ChipFilter;
  /** 안 읽은 것만 — 처음 열면 켜져 있다 */
  unreadOnly: boolean;
  /** 입력 그대로(앞뒤 공백은 요청 때 지운다) */
  query: string;
}

/** 주소 쿼리에 두는 화면 상태 */
export interface NotificationView {
  filters: NotificationFilters;
  /** 1부터 */
  page: number;
}

/**
 * 주소 쿼리 → 화면 상태. 기본값(전체 · 전체 · 안 읽은 것만 켬 · 검색 없음 · 1페이지)은 주소에 없고, 모르는 값은 기본값으로 본다.
 * 예: ?priority=URGENT&chip=EVENT&unreadOnly=false&q=쿠폰&page=2
 */
export function viewFromParams(params: URLSearchParams): NotificationView {
  const priority = PRIORITY_FILTERS.find((p) => p.key === params.get('priority'));
  const chip = NOTIFICATION_CHIPS.find((c) => c.key === params.get('chip'));
  const page = Number(params.get('page'));
  return {
    filters: {
      priority: priority?.key ?? 'ALL',
      chip: chip?.key ?? 'ALL',
      unreadOnly: params.get('unreadOnly') !== 'false',
      query: (params.get('q') ?? '').slice(0, QUERY_MAX),
    },
    page: Number.isInteger(page) && page >= 1 && page <= PAGE_MAX ? page : 1,
  };
}

/** 화면 상태 → 주소 쿼리. 기본값은 뺀다 */
export function paramsFromView({ filters, page }: NotificationView): URLSearchParams {
  const params = new URLSearchParams();
  if (filters.priority !== 'ALL') params.set('priority', filters.priority);
  if (filters.chip !== 'ALL') params.set('chip', filters.chip);
  if (!filters.unreadOnly) params.set('unreadOnly', 'false');
  if (filters.query.trim()) params.set('q', filters.query);
  if (page > 1) params.set('page', String(page));
  return params;
}

/** 조건을 바꾸면 1페이지로 */
export function withFilters(view: NotificationView, change: Partial<NotificationFilters>): NotificationView {
  return { filters: { ...view.filters, ...change }, page: 1 };
}

/** 목록 API 쿼리 — 전체는 보내지 않고, 안 읽은 것만 · 페이지 · 크기는 늘 보낸다(서버 기본값은 안 읽은 것만 끔) */
export function listQuery({ filters, page }: NotificationView): URLSearchParams {
  const params = new URLSearchParams();
  if (filters.priority !== 'ALL') params.set('priority', filters.priority);
  if (filters.chip !== 'ALL') params.set('chip', filters.chip);
  params.set('unreadOnly', String(filters.unreadOnly));
  const q = filters.query.trim();
  if (q) params.set('q', q);
  params.set('page', String(page));
  params.set('size', String(PAGE_SIZE));
  return params;
}

/** 범위 밖 페이지(빈 목록인데 마지막 페이지보다 뒤 — 마지막 페이지를 다 읽은 뒤 등)면 고칠 페이지, 아니면 null. 맞는 알림이 없으면 1페이지 */
export function pageToFix(data: NotificationPage): number | null {
  const last = Math.max(data.totalPages, 1);
  return data.items.length === 0 && data.page > last ? last : null;
}

export interface EmptyState {
  title: string;
  description?: string;
  /** [모든 알림 보기] — 안 읽은 것만을 끈다 */
  showAll: boolean;
}

/** 빈 목록 문구 — 검색어가 있으면 검색 결과 없음, 안 읽은 것만이면 끄라는 안내, 그 밖은 알림 없음 */
export function emptyState(filters: NotificationFilters): EmptyState {
  if (filters.query.trim()) return { title: '검색 결과가 없습니다', showAll: false };
  if (filters.unreadOnly) {
    return { title: '안 읽은 알림이 없습니다', description: "'안 읽은 것만'을 끄면 읽은 알림도 볼 수 있습니다.", showAll: true };
  }
  return { title: '알림이 없습니다', showAll: false };
}

/** [모두 읽음] — 안 읽은 알림이(조건과 무관하게) 있고 요청 중이 아닐 때만 */
export function canReadAll(data: NotificationPage, pending: boolean): boolean {
  return !pending && data.unreadCount > 0;
}

/**
 * 문구를 첫 문장(첫 줄)과 나머지(둘째 줄, 2줄 말줄임)로 — 문장 끝(. ! ?) 뒤 공백에서 한 번 나눈다(Console splitContent와 같은 규칙).
 * 예: '[1043:봄 쿠폰] 이벤트 신청이 반려되었습니다. 반려 사유: 기간이 겹칩니다.' → 첫 문장 / '반려 사유: 기간이 겹칩니다.'
 * 앞뒤 공백은 먼저 지운다. 소수점(2.0) · 시각(10:00) · 콜론은 나누지 않는다
 */
export function splitContent(content: string): { head: string; rest: string } {
  const text = content.trim();
  const end = /[.!?]\s+/.exec(text);
  if (!end) return { head: text, rest: '' };
  return { head: text.slice(0, end.index + 1), rest: text.slice(end.index + end[0].length).trim() };
}

/** 이동 경로 — 같은 앱 안의 경로('/'로 시작, '//'는 아님)만 링크로. 없으면 null(누르면 읽음만) */
export function itemHref(n: PortalNotification): string | null {
  const link = n.link;
  return link && link.startsWith('/') && !link.startsWith('//') ? link : null;
}

/**
 * 항목 클릭(왼쪽) · 가운데 클릭(새 탭): 안 읽은 것만 읽음 요청. 요청을 기다리지 않고 기본 동작(링크 이동 · 새 탭)도 막지 않는다 —
 * 읽음 요청이 실패해도 이동은 그대로(되돌리기 · 토스트는 readOptimistically). 오른쪽 클릭(메뉴)은 읽음이 아니다
 */
export function pressItem(n: PortalNotification, event: { button: number }, onRead: (notificationId: number) => void): void {
  if (!n.read && (event.button === 0 || event.button === 1)) onRead(n.notificationId);
}

/** 한 건의 읽음 여부를 바꾼 새 목록 (클릭 시 먼저 바꾸고, 실패하면 되돌린다) */
export function withRead(items: PortalNotification[], notificationId: number, read: boolean): PortalNotification[] {
  return items.map((n) => (n.notificationId === notificationId ? { ...n, read } : n));
}

/** 읽음: 화면을 먼저 바꾸고 요청, 실패하면 되돌린 뒤 onError(토스트) */
export async function readOptimistically(
  notificationId: number,
  update: (change: (items: PortalNotification[]) => PortalNotification[]) => void,
  request: (notificationId: number) => Promise<void>,
  onError: (error: unknown) => void,
): Promise<void> {
  update((items) => withRead(items, notificationId, true));
  try {
    await request(notificationId);
  } catch (e) {
    update((items) => withRead(items, notificationId, false));
    onError(e);
  }
}
