import type { NotificationChip, NotificationPriority, PortalNotification } from '@/types';

/**
 * UI-TP-13 알림센터 필터 · 검색 · 건수 · 이동 · 읽음 처리 — 화면과 분리, node --test 대상.
 * 서버는 전체를 최신순으로 주고, 항목마다 칩 · 이동 경로를 계산해 준다.
 * 필터는 한 줄 — 우선순위(세그먼트) AND 분류(드롭다운) AND 안 읽은 것만(토글) AND 내용 검색.
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

/** 화면에서 고른 조건 */
export interface NotificationFilters {
  priority: PriorityFilter;
  chip: ChipFilter;
  /** 안 읽은 것만 */
  unreadOnly: boolean;
  query: string;
}

export function matchesPriority(n: PortalNotification, priority: PriorityFilter): boolean {
  return priority === 'ALL' || n.priority === priority;
}

export function matchesChip(n: PortalNotification, chip: ChipFilter): boolean {
  return chip === 'ALL' || n.chip === chip;
}

export function matchesUnread(n: PortalNotification, unreadOnly: boolean): boolean {
  return !unreadOnly || !n.read;
}

/** 우선순위 건수 — 안 읽은 것만 조건 안에서(고른 분류 · 검색과 무관) */
export function countByPriority(
  items: PortalNotification[],
  { unreadOnly }: Pick<NotificationFilters, 'unreadOnly'>,
): Record<PriorityFilter, number> {
  const inScope = items.filter((n) => matchesUnread(n, unreadOnly));
  return Object.fromEntries(
    PRIORITY_FILTERS.map((p) => [p.key, inScope.filter((n) => matchesPriority(n, p.key)).length]),
  ) as Record<PriorityFilter, number>;
}

/** 분류 건수 — 지금 우선순위 AND 안 읽은 것만 안에서(고른 분류 · 검색과 무관). 칩이 없는 알림은 '전체'에만 센다 */
export function countByChip(
  items: PortalNotification[],
  { priority, unreadOnly }: Pick<NotificationFilters, 'priority' | 'unreadOnly'>,
): Record<ChipFilter, number> {
  const inScope = items.filter((n) => matchesPriority(n, priority) && matchesUnread(n, unreadOnly));
  return Object.fromEntries(
    NOTIFICATION_CHIPS.map((c) => [c.key, inScope.filter((n) => matchesChip(n, c.key)).length]),
  ) as Record<ChipFilter, number>;
}

export interface ChipMenuItem {
  key: ChipFilter;
  label: string;
  count: number;
  /** 0건 — 흐리게만, 고를 수는 있다 */
  dim: boolean;
}

/** 분류 메뉴 항목 — 이름 · 건수(countByChip) */
export function chipMenuItems(items: PortalNotification[], scope: Pick<NotificationFilters, 'priority' | 'unreadOnly'>): ChipMenuItem[] {
  const counts = countByChip(items, scope);
  return NOTIFICATION_CHIPS.map((c) => ({ ...c, count: counts[c.key], dim: counts[c.key] === 0 }));
}

/** 분류 버튼 글자 — '분류: 전체', 고르면 '분류: 이벤트' */
export function chipButtonLabel(chip: ChipFilter): string {
  return `분류: ${chip === 'ALL' ? '전체' : CHIP_LABEL[chip]}`;
}

/** 보이는 목록 — 우선순위 AND 분류 AND 안 읽은 것만 AND 내용 검색(앞뒤 공백 · 대소문자 무시, 입력 즉시). 최신순(같은 시각은 나중 알림 먼저) */
export function visibleNotifications(items: PortalNotification[], filters: NotificationFilters): PortalNotification[] {
  const { priority, chip, unreadOnly } = filters;
  const q = filters.query.trim().toLowerCase();
  // ISO +09:00 문자열이라 글자 비교 = 시각 비교
  return items
    .filter(
      (n) =>
        matchesPriority(n, priority) &&
        matchesChip(n, chip) &&
        matchesUnread(n, unreadOnly) &&
        (q === '' || n.content.toLowerCase().includes(q)),
    )
    .sort((a, b) => b.occurredAt.localeCompare(a.occurredAt) || b.notificationId - a.notificationId);
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
