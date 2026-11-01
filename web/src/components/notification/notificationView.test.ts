import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import type { NotificationChip, NotificationPage, NotificationPriority, PortalNotification } from '../../types/index.ts';
import { priorityMeta } from '../../utils/status.ts';
import {
  NOTIFICATION_CHIPS,
  PRIORITY_FILTERS,
  canReadAll,
  chipButtonLabel,
  chipMenuItems,
  emptyState,
  itemHref,
  listQuery,
  pageToFix,
  paramsFromView,
  pressItem,
  readOptimistically,
  splitContent,
  viewFromParams,
  withFilters,
  withRead,
  type NotificationFilters,
  type NotificationView,
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

/** 처음 열 때 조건(전체 · 전체 · 안 읽은 것만 켬 · 검색 없음)에서 일부만 바꾼 조건 */
const filters = (change: Partial<NotificationFilters> = {}): NotificationFilters => ({
  priority: 'ALL',
  chip: 'ALL',
  unreadOnly: true,
  query: '',
  ...change,
});

const view = (change: Partial<NotificationFilters> = {}, page = 1): NotificationView => ({ filters: filters(change), page });

/** 서버 응답 한 페이지(건수는 테스트에 쓰지 않는 값) */
const pageOf = (change: Partial<NotificationPage> = {}): NotificationPage => ({
  items: ITEMS,
  page: 1,
  size: 10,
  totalCount: ITEMS.length,
  totalPages: 1,
  unreadCount: 3,
  counts: { priority: { ALL: 3, URGENT: 1, WARNING: 1, INFO: 1 }, chip: { ALL: 3, APPLICATION: 0, EVENT: 2, REPORT: 0 } },
  ...change,
});

const parse = (query: string) => viewFromParams(new URLSearchParams(query));

describe('우선순위 세그먼트 · 분류 드롭다운', () => {
  test('세그먼트는 전체 · 긴급 · 주의 · 정보 — 안읽음은 세그먼트에 없다', () => {
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

  test('분류 메뉴는 전체 + 신청 · 이벤트 · 리포트(콘솔과 같은 순서)', () => {
    assert.deepEqual(
      NOTIFICATION_CHIPS.map((c) => c.label),
      ['전체', '신청', '이벤트', '리포트'],
    );
  });

  test('분류 버튼 글자 — 기본 분류: 전체, 고르면 분류: 이름', () => {
    assert.equal(chipButtonLabel('ALL'), '분류: 전체');
    assert.equal(chipButtonLabel('APPLICATION'), '분류: 신청');
    assert.equal(chipButtonLabel('EVENT'), '분류: 이벤트');
    assert.equal(chipButtonLabel('REPORT'), '분류: 리포트');
  });

  test('분류 메뉴 건수는 서버 건수 그대로, 0건은 흐리게(dim)만 — 메뉴에서 빠지지 않는다', () => {
    assert.deepEqual(
      chipMenuItems({ ALL: 2, APPLICATION: 1, EVENT: 0, REPORT: 0 }).map((c) => [c.key, c.count, c.dim]),
      [
        ['ALL', 2, false],
        ['APPLICATION', 1, false],
        ['EVENT', 0, true],
        ['REPORT', 0, true],
      ],
    );
  });
});

describe('주소 쿼리', () => {
  test('처음 열면(쿼리 없음) 안 읽은 것만 켬 · 1페이지', () => {
    assert.deepEqual(parse(''), view());
  });

  test('조건 · 페이지를 주소에 남기고 그대로 읽는다(기본값은 뺀다)', () => {
    const v = view({ priority: 'URGENT', chip: 'EVENT', unreadOnly: false, query: '봄 쿠폰' }, 3);
    const params = paramsFromView(v);
    assert.equal(params.toString(), 'priority=URGENT&chip=EVENT&unreadOnly=false&q=%EB%B4%84+%EC%BF%A0%ED%8F%B0&page=3');
    assert.deepEqual(viewFromParams(params), v);
    assert.equal(paramsFromView(view()).toString(), '');
  });

  test('검색어는 입력 그대로 남기고(입력 중 뒤 공백 유지), 공백뿐이면 뺀다', () => {
    assert.equal(paramsFromView(view({ query: '봄 ' })).get('q'), '봄 ');
    assert.equal(paramsFromView(view({ query: '   ' })).has('q'), false);
  });

  test('모르는 값은 기본값으로 본다', () => {
    assert.deepEqual(parse('priority=urgent&chip=TENANT&unreadOnly=no&page=abc'), view());
    for (const page of ['0', '-1', '1.5', '10001']) {
      assert.equal(parse(`page=${page}`).page, 1, page);
    }
    assert.equal(parse('page=10000').page, 10000);
    assert.equal(parse(`q=${'가'.repeat(120)}`).filters.query.length, 100);
  });

  test('조건을 바꾸면 1페이지로', () => {
    assert.deepEqual(withFilters(view({}, 4), { chip: 'REPORT' }), view({ chip: 'REPORT' }, 1));
    assert.deepEqual(withFilters(view({ priority: 'INFO' }, 2), { unreadOnly: false }), view({ priority: 'INFO', unreadOnly: false }, 1));
  });
});

describe('목록 요청', () => {
  test('처음 열면 안 읽은 것만 · 1페이지 · 10건', () => {
    assert.equal(listQuery(view()).toString(), 'unreadOnly=true&page=1&size=10');
  });

  test('전체는 보내지 않고 검색어는 앞뒤 공백을 지워 보낸다', () => {
    const q = listQuery(view({ priority: 'WARNING', chip: 'APPLICATION', unreadOnly: false, query: '  쿠폰 ' }, 2));
    assert.deepEqual(Object.fromEntries(q), { priority: 'WARNING', chip: 'APPLICATION', unreadOnly: 'false', q: '쿠폰', page: '2', size: '10' });
    assert.equal(listQuery(view({ query: '   ' })).has('q'), false);
  });

  test('범위 밖 페이지면 마지막 페이지로, 맞는 알림이 없으면 1페이지로', () => {
    assert.equal(pageToFix(pageOf({ items: [], page: 5, totalPages: 3 })), 3);
    assert.equal(pageToFix(pageOf({ items: [], page: 2, totalCount: 0, totalPages: 0 })), 1);
    assert.equal(pageToFix(pageOf({ items: [], page: 1, totalCount: 0, totalPages: 0 })), null);
    assert.equal(pageToFix(pageOf({ page: 3, totalPages: 3 })), null);
  });
});

describe('빈 목록 · 모두 읽음', () => {
  test('빈 목록 문구 3가지 — 검색어 > 안 읽은 것만 > 그 밖', () => {
    assert.deepEqual(emptyState(filters({ query: '쿠폰' })), { title: '검색 결과가 없습니다', showAll: false });
    assert.deepEqual(emptyState(filters()), {
      title: '안 읽은 알림이 없습니다',
      description: "'안 읽은 것만'을 끄면 읽은 알림도 볼 수 있습니다.",
      showAll: true,
    });
    assert.deepEqual(emptyState(filters({ unreadOnly: false })), { title: '알림이 없습니다', showAll: false });
    assert.equal(emptyState(filters({ query: '   ' })).title, '안 읽은 알림이 없습니다');
  });

  test('모두 읽음은 안 읽은 알림이 있을 때만(조건 · 지금 페이지와 무관), 요청 중에는 막는다', () => {
    assert.equal(canReadAll(pageOf({ unreadCount: 3 }), false), true);
    assert.equal(canReadAll(pageOf({ unreadCount: 0 }), false), false);
    assert.equal(canReadAll(pageOf({ unreadCount: 3 }), true), false);
    // 지금 목록이 비어 있어도(다른 조건) 안 읽은 알림이 있으면 누를 수 있다
    assert.equal(canReadAll(pageOf({ items: [], totalCount: 0, totalPages: 0, unreadCount: 2 }), false), true);
  });
});

describe('항목 문구 — 첫 문장 · 나머지', () => {
  test('두 문장이면 첫 문장 / 나머지', () => {
    assert.deepEqual(splitContent('[1043:봄 쿠폰] 이벤트 신청이 접수되지 않았습니다. 다시 신청해 주세요. 원인: 배너 업로드 시간 초과'), {
      head: '[1043:봄 쿠폰] 이벤트 신청이 접수되지 않았습니다.',
      rest: '다시 신청해 주세요. 원인: 배너 업로드 시간 초과',
    });
  });

  test('한 문장이면 나머지 없음(둘째 줄을 그리지 않는다)', () => {
    assert.deepEqual(splitContent('[1043:봄 쿠폰] 이벤트 신청이 접수되었습니다.'), { head: '[1043:봄 쿠폰] 이벤트 신청이 접수되었습니다.', rest: '' });
    // 문장 끝 부호 뒤 공백만 있으면 그대로 한 문장
    assert.deepEqual(splitContent('[1043:봄 쿠폰] 호스팅을 시작했습니다. '), { head: '[1043:봄 쿠폰] 호스팅을 시작했습니다.', rest: '' });
  });

  test('콜론이 있는 문장(반려 사유 · 원인)은 나머지로, 이벤트 표기의 콜론 · 소수점 · 시각은 나누지 않는다', () => {
    assert.deepEqual(splitContent('[1043:봄 쿠폰] 이벤트 신청이 반려되었습니다. 반려 사유: 이벤트 기간에 정기 점검이 있습니다.'), {
      head: '[1043:봄 쿠폰] 이벤트 신청이 반려되었습니다.',
      rest: '반려 사유: 이벤트 기간에 정기 점검이 있습니다.',
    });
    assert.deepEqual(splitContent('[1043:쿠폰 2.0] 입장이 열렸습니다(10:00).'), { head: '[1043:쿠폰 2.0] 입장이 열렸습니다(10:00).', rest: '' });
  });

  test('! · ? 도 문장 끝, 마침표가 없으면 통째로 첫 줄', () => {
    assert.deepEqual(splitContent('품절되었습니다! 결과를 확인하세요?  곧 집계합니다'), { head: '품절되었습니다!', rest: '결과를 확인하세요?  곧 집계합니다' });
    assert.deepEqual(splitContent('정기 점검 안내'), { head: '정기 점검 안내', rest: '' });
  });

  test('빈 문자열 · 앞뒤 공백', () => {
    assert.deepEqual(splitContent(''), { head: '', rest: '' });
    assert.deepEqual(splitContent('   '), { head: '', rest: '' });
    assert.deepEqual(splitContent('  첫 문장입니다.   둘째 문장입니다.  '), { head: '첫 문장입니다.', rest: '둘째 문장입니다.' });
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
