import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import type { NotificationChip, NotificationPriority, PortalNotification } from '../../types/index.ts';
import { priorityMeta } from '../../utils/status.ts';
import {
  NOTIFICATION_CHIPS,
  NOTIFICATION_FILTERS,
  countByChip,
  countByFilter,
  itemHref,
  pressItem,
  readOptimistically,
  visibleNotifications,
  withRead,
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

describe('알림센터 탭 · 건수', () => {
  test('탭 5종 — 전체보기 · 긴급 · 주의 · 정보 · 안읽음', () => {
    assert.deepEqual(
      NOTIFICATION_FILTERS.map((f) => f.label),
      ['전체보기', '긴급', '주의', '정보', '안읽음'],
    );
  });

  test('우선순위 이름은 탭과 태그가 같다(경고 아님)', () => {
    for (const p of ['URGENT', 'WARNING', 'INFO'] as const) {
      assert.equal(NOTIFICATION_FILTERS.find((f) => f.key === p)?.label, priorityMeta[p].label);
    }
    assert.equal(priorityMeta.WARNING.label, '주의');
  });

  test('탭 건수는 전체 목록 기준', () => {
    assert.deepEqual(countByFilter(ITEMS), { ALL: 5, URGENT: 1, WARNING: 2, INFO: 2, UNREAD: 3 });
    assert.deepEqual(countByFilter([]), { ALL: 0, URGENT: 0, WARNING: 0, INFO: 0, UNREAD: 0 });
  });
});

describe('칩 필터 · 건수', () => {
  test('칩은 전체 + 신청 · 이벤트 · 리포트(콘솔과 같은 순서)', () => {
    assert.deepEqual(
      NOTIFICATION_CHIPS.map((c) => c.label),
      ['전체', '신청', '이벤트', '리포트'],
    );
  });

  test('칩 건수는 지금 탭 기준(탭과 AND), 칩이 없는 알림은 전체에만', () => {
    assert.deepEqual(countByChip(ITEMS, 'ALL'), { ALL: 5, APPLICATION: 1, EVENT: 2, REPORT: 1 });
    assert.deepEqual(countByChip(ITEMS, 'WARNING'), { ALL: 2, APPLICATION: 1, EVENT: 0, REPORT: 0 });
    assert.deepEqual(countByChip(ITEMS, 'UNREAD'), { ALL: 3, APPLICATION: 0, EVENT: 2, REPORT: 0 });
  });

  test('칩이 없는 알림은 칩을 고르면 안 보인다', () => {
    assert.ok(ids(visibleNotifications(ITEMS, 'ALL', 'ALL', '')).includes(5));
    for (const chip of ['APPLICATION', 'EVENT', 'REPORT'] as const) {
      assert.ok(!ids(visibleNotifications(ITEMS, 'ALL', chip, '')).includes(5), chip);
    }
  });
});

describe('보이는 목록', () => {
  test('최신순, 같은 시각은 나중 알림 먼저', () => {
    assert.deepEqual(ids(visibleNotifications(ITEMS, 'ALL', 'ALL', '')), [4, 3, 2, 1, 5]);
  });

  test('탭 AND 칩 AND 검색', () => {
    assert.deepEqual(ids(visibleNotifications(ITEMS, 'UNREAD', 'EVENT', '')), [3, 1]);
    assert.deepEqual(ids(visibleNotifications(ITEMS, 'INFO', 'EVENT', '')), [3]);
    assert.deepEqual(ids(visibleNotifications(ITEMS, 'ALL', 'EVENT', '봄 쿠폰')), [1]);
  });

  test('검색은 내용만, 앞뒤 공백 · 대소문자 무시', () => {
    assert.deepEqual(ids(visibleNotifications(ITEMS, 'ALL', 'ALL', '  fall SALE ')), [3]);
    assert.deepEqual(ids(visibleNotifications(ITEMS, 'ALL', 'ALL', '없는 말')), []);
  });

  test('원본 배열은 바꾸지 않는다', () => {
    const before = ids(ITEMS);
    visibleNotifications(ITEMS, 'ALL', 'ALL', '');
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
