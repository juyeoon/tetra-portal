import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import type { NotificationChip, NotificationPriority, PortalNotification } from '../../types/index.ts';
import { priorityMeta } from '../../utils/status.ts';
import {
  NOTIFICATION_CHIPS,
  PRIORITY_FILTERS,
  chipButtonLabel,
  chipMenuItems,
  countByChip,
  countByPriority,
  itemHref,
  pressItem,
  readOptimistically,
  visibleNotifications,
  withRead,
  type NotificationFilters,
} from './notificationView.ts';

const item = (
  notificationId: number,
  priority: NotificationPriority,
  read: boolean,
  occurredAt: string,
  chip: NotificationChip | null,
  content = `알림 ${notificationId}`,
  link: string | null = null,
): PortalNotification => ({
  notificationId,
  eventId: null,
  category: 'HOSTING',
  priority,
  occurredAt,
  content,
  read,
  chip,
  link,
});

const ITEMS = [
  item(1, 'URGENT', false, '2026-10-21T10:00:00+09:00', 'EVENT', '[1043:봄 쿠폰] 이벤트가 종료되었습니다.'),
  item(2, 'WARNING', true, '2026-10-21T11:00:00+09:00', 'APPLICATION', '[1044:여름 쿠폰] 이벤트 신청이 반려되었습니다.'),
  item(3, 'INFO', false, '2026-10-21T12:00:00+09:00', 'EVENT', '[1045:Fall Sale] 호스팅을 시작했습니다.'),
  item(4, 'INFO', true, '2026-10-21T12:00:00+09:00', 'REPORT', '9월 리포트가 생성되었습니다.'),
  item(5, 'WARNING', false, '2026-10-21T09:00:00+09:00', null, '정기 점검 안내'),
];

const ids = (list: PortalNotification[]) => list.map((n) => n.notificationId);

/** 기본 조건(전체 · 전체 · 안 읽은 것만 끔 · 검색 없음)에서 일부만 바꾼 조건 */
const filters = (change: Partial<NotificationFilters> = {}): NotificationFilters => ({
  priority: 'ALL',
  chip: 'ALL',
  unreadOnly: false,
  query: '',
  ...change,
});

const visibleIds = (change: Partial<NotificationFilters> = {}) => ids(visibleNotifications(ITEMS, filters(change)));

describe('우선순위 세그먼트 · 건수', () => {
  test('전체 · 긴급 · 주의 · 정보 — 안읽음은 세그먼트에 없다', () => {
    assert.deepEqual(
      PRIORITY_FILTERS.map((p) => p.label),
      ['전체', '긴급', '주의', '정보'],
    );
  });

  test('우선순위 이름은 세그먼트와 태그가 같다(경고 아님)', () => {
    for (const p of ['URGENT', 'WARNING', 'INFO'] as const) {
      assert.equal(PRIORITY_FILTERS.find((f) => f.key === p)?.label, priorityMeta[p].label);
    }
    assert.equal(priorityMeta.WARNING.label, '주의');
  });

  test('우선순위 건수는 안 읽은 것만 조건을 따른다', () => {
    assert.deepEqual(countByPriority(ITEMS, { unreadOnly: false }), { ALL: 5, URGENT: 1, WARNING: 2, INFO: 2 });
    assert.deepEqual(countByPriority(ITEMS, { unreadOnly: true }), { ALL: 3, URGENT: 1, WARNING: 1, INFO: 1 });
    assert.deepEqual(countByPriority([], { unreadOnly: true }), { ALL: 0, URGENT: 0, WARNING: 0, INFO: 0 });
  });

  test('우선순위 건수는 고른 분류 · 검색어와 무관', () => {
    assert.deepEqual(countByPriority(ITEMS, filters({ chip: 'REPORT', query: '봄 쿠폰' })), countByPriority(ITEMS, filters()));
    assert.deepEqual(countByPriority(ITEMS, filters({ chip: 'EVENT', unreadOnly: true, query: '없는 말' })), { ALL: 3, URGENT: 1, WARNING: 1, INFO: 1 });
  });
});

describe('분류 드롭다운 · 건수', () => {
  test('메뉴는 전체 + 신청 · 이벤트 · 리포트(콘솔과 같은 순서)', () => {
    assert.deepEqual(
      NOTIFICATION_CHIPS.map((c) => c.label),
      ['전체', '신청', '이벤트', '리포트'],
    );
  });

  test('버튼 글자 — 기본 분류: 전체, 고르면 분류: 이름', () => {
    assert.equal(chipButtonLabel('ALL'), '분류: 전체');
    assert.equal(chipButtonLabel('APPLICATION'), '분류: 신청');
    assert.equal(chipButtonLabel('EVENT'), '분류: 이벤트');
    assert.equal(chipButtonLabel('REPORT'), '분류: 리포트');
  });

  test('분류 건수는 지금 우선순위 · 안 읽은 것만 조건을 따른다', () => {
    assert.deepEqual(countByChip(ITEMS, { priority: 'ALL', unreadOnly: false }), { ALL: 5, APPLICATION: 1, EVENT: 2, REPORT: 1 });
    assert.deepEqual(countByChip(ITEMS, { priority: 'WARNING', unreadOnly: false }), { ALL: 2, APPLICATION: 1, EVENT: 0, REPORT: 0 });
    assert.deepEqual(countByChip(ITEMS, { priority: 'ALL', unreadOnly: true }), { ALL: 3, APPLICATION: 0, EVENT: 2, REPORT: 0 });
    assert.deepEqual(countByChip(ITEMS, { priority: 'INFO', unreadOnly: true }), { ALL: 1, APPLICATION: 0, EVENT: 1, REPORT: 0 });
  });

  test('분류 건수는 고른 분류 · 검색어와 무관', () => {
    const counts = (f: NotificationFilters) => chipMenuItems(ITEMS, f).map((c) => c.count);
    assert.deepEqual(counts(filters({ chip: 'REPORT', query: '봄 쿠폰' })), counts(filters()));
  });

  test('칩이 없는 알림은 분류 전체에만 세고, 분류를 고르면 안 보인다', () => {
    // 5번(주의 · 안 읽음 · 칩 없음)만 남는 조건
    assert.deepEqual(countByChip(ITEMS, { priority: 'WARNING', unreadOnly: true }), { ALL: 1, APPLICATION: 0, EVENT: 0, REPORT: 0 });
    assert.ok(visibleIds().includes(5));
    for (const chip of ['APPLICATION', 'EVENT', 'REPORT'] as const) {
      assert.ok(!visibleIds({ chip }).includes(5), chip);
    }
  });

  test('0건 항목은 흐리게(dim)만 — 메뉴에 그대로 있고 고르면 빈 목록', () => {
    const items = chipMenuItems(ITEMS, filters({ priority: 'WARNING' }));
    assert.deepEqual(
      items.map((c) => [c.key, c.count, c.dim]),
      [
        ['ALL', 2, false],
        ['APPLICATION', 1, false],
        ['EVENT', 0, true],
        ['REPORT', 0, true],
      ],
    );
    assert.deepEqual(visibleIds({ priority: 'WARNING', chip: 'EVENT' }), []);
  });
});

describe('보이는 목록', () => {
  test('최신순, 같은 시각은 나중 알림 먼저', () => {
    assert.deepEqual(visibleIds(), [4, 3, 2, 1, 5]);
  });

  test('우선순위 AND 분류 AND 안 읽은 것만 AND 검색', () => {
    assert.deepEqual(visibleIds({ unreadOnly: true }), [3, 1, 5]);
    assert.deepEqual(visibleIds({ chip: 'EVENT', unreadOnly: true }), [3, 1]);
    assert.deepEqual(visibleIds({ priority: 'INFO', chip: 'EVENT' }), [3]);
    assert.deepEqual(visibleIds({ priority: 'INFO', unreadOnly: true }), [3]);
    assert.deepEqual(visibleIds({ priority: 'INFO', chip: 'REPORT', unreadOnly: true }), []);
    assert.deepEqual(visibleIds({ priority: 'WARNING', unreadOnly: true }), [5]);
    assert.deepEqual(visibleIds({ priority: 'URGENT', chip: 'EVENT', unreadOnly: true, query: '봄 쿠폰' }), [1]);
    assert.deepEqual(visibleIds({ chip: 'EVENT', unreadOnly: true, query: '여름' }), []);
  });

  test('검색은 내용만, 앞뒤 공백 · 대소문자 무시', () => {
    assert.deepEqual(visibleIds({ query: '  fall SALE ' }), [3]);
    assert.deepEqual(visibleIds({ query: '없는 말' }), []);
  });

  test('원본 배열은 바꾸지 않는다', () => {
    const before = ids(ITEMS);
    visibleNotifications(ITEMS, filters());
    assert.deepEqual(ids(ITEMS), before);
  });
});

describe('링크 · 읽음', () => {
  test('href는 서버 경로 그대로, 앱 밖 주소는 링크로 만들지 않는다', () => {
    const at = '2026-10-21T10:00:00+09:00';
    assert.equal(itemHref(item(1, 'INFO', false, at, 'EVENT', '', '/events/1043?tab=result')), '/events/1043?tab=result');
    assert.equal(itemHref(item(1, 'INFO', false, at, 'APPLICATION', '', '/events/1043')), '/events/1043');
    assert.equal(itemHref(item(1, 'INFO', false, at, 'REPORT', '', '/reports')), '/reports');
    assert.equal(itemHref(item(1, 'INFO', false, at, 'EVENT', '', null)), null);
    assert.equal(itemHref(item(1, 'INFO', false, at, 'EVENT', '', '')), null);
    assert.equal(itemHref(item(1, 'INFO', false, at, 'EVENT', '', '//evil.example.com')), null);
    assert.equal(itemHref(item(1, 'INFO', false, at, 'EVENT', '', 'https://evil.example.com')), null);
  });

  test('클릭 · 가운데 클릭은 안 읽은 것만 읽음, 오른쪽 클릭은 아님', () => {
    const read: number[] = [];
    const onRead = (id: number) => read.push(id);
    const unread = ITEMS[0];
    const already = ITEMS[1];
    pressItem(unread, { button: 0 }, onRead);
    pressItem(unread, { button: 1 }, onRead);
    pressItem(unread, { button: 2 }, onRead);
    pressItem(already, { button: 0 }, onRead);
    pressItem(already, { button: 1 }, onRead);
    assert.deepEqual(read, [1, 1]);
  });

  test('읽음이 실패하면 되돌린 뒤 토스트(이동은 기다리지 않으므로 막지 않는다)', async () => {
    let items = ITEMS;
    const errors: unknown[] = [];
    const seen: boolean[] = [];
    await readOptimistically(
      1,
      (change) => {
        items = change(items);
        seen.push(items.find((n) => n.notificationId === 1)!.read);
      },
      async () => {
        throw new Error('네트워크 오류');
      },
      (e) => errors.push(e),
    );
    assert.deepEqual(seen, [true, false]);
    assert.equal(items.find((n) => n.notificationId === 1)?.read, false);
    assert.equal(errors.length, 1);
  });

  test('읽음이 성공하면 그대로 읽음, 토스트 없음', async () => {
    let items = ITEMS;
    const errors: unknown[] = [];
    const requested: number[] = [];
    await readOptimistically(
      3,
      (change) => {
        items = change(items);
      },
      async (id) => {
        requested.push(id);
      },
      (e) => errors.push(e),
    );
    assert.deepEqual(requested, [3]);
    assert.equal(items.find((n) => n.notificationId === 3)?.read, true);
    assert.equal(errors.length, 0);
  });

  test('한 건만 바꾸고 원본은 그대로', () => {
    const next = withRead(ITEMS, 1, true);
    assert.deepEqual(
      next.map((n) => n.read),
      [true, true, false, true, false],
    );
    assert.equal(ITEMS[0].read, false);
  });
});
