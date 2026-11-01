import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import {
  NO_UNREAD,
  applyUnread,
  badgeText,
  badgeTitle,
  clearUnread,
  createBatch,
  createWriteRefresher,
  fetchOnRoute,
  followUpAfterWrite,
  navAriaLabel,
  refreshAfterWrite,
  shouldPoll,
  type Timers,
} from './unreadCount.ts';

describe('사이드바 안 읽은 알림 배지 — 글자 · 읽기 이름', () => {
  test('0 · 모름이면 배지 없음, 1~99는 그대로, 100 이상은 99+', () => {
    assert.equal(badgeText(0), null);
    assert.equal(badgeText(null), null);
    assert.equal(badgeText(1), '1');
    assert.equal(badgeText(5), '5');
    assert.equal(badgeText(99), '99');
    assert.equal(badgeText(100), '99+');
    assert.equal(badgeText(1234), '99+');
  });

  test('메뉴 링크 aria-label — 0 · 모름이면 메뉴 이름만, 99+여도 실제 수', () => {
    assert.equal(navAriaLabel(0), '알림 센터');
    assert.equal(navAriaLabel(null), '알림 센터');
    assert.equal(navAriaLabel(5), '알림 센터, 안 읽은 알림 5건');
    assert.equal(navAriaLabel(120), '알림 센터, 안 읽은 알림 120건');
  });

  test('마우스를 올리면 안 읽은 알림 N건', () => {
    assert.equal(badgeTitle(5), '안 읽은 알림 5건');
    assert.equal(badgeTitle(120), '안 읽은 알림 120건');
  });
});

describe('사이드바 안 읽은 알림 배지 — 갱신 판단', () => {
  test('주기 · 다시 보일 때 — 탭이 숨겨졌거나 로그아웃이면 건너뛴다', () => {
    assert.equal(shouldPoll({ authenticated: true, hidden: false }), true);
    assert.equal(shouldPoll({ authenticated: true, hidden: true }), false);
    assert.equal(shouldPoll({ authenticated: false, hidden: false }), false);
  });

  test('라우트 이동 — 알림센터(목록 응답으로 맞춤) · 로그인 전 화면은 따로 받지 않는다', () => {
    assert.equal(fetchOnRoute('/events'), true);
    assert.equal(fetchOnRoute('/events/1043'), true);
    assert.equal(fetchOnRoute('/settings'), true);
    assert.equal(fetchOnRoute('/notifications'), false);
    assert.equal(fetchOnRoute('/login'), false);
    assert.equal(fetchOnRoute('/password/setup'), false);
  });

  test('실패하면 이전 값 그대로', () => {
    const got = applyUnread(NO_UNREAD, 1, 5);
    assert.deepEqual(got, { count: 5, applied: 1 });
    assert.deepEqual(applyUnread(got, 2, null), got);
    // 아직 한 번도 못 받았으면 모름(배지 없음)
    assert.deepEqual(applyUnread(NO_UNREAD, 1, null), NO_UNREAD);
  });

  test('앞선 요청의 늦은 응답은 버린다(목록 응답으로 맞춘 값이 이긴다)', () => {
    // 요청 1(배지 조회) 보냄 → 목록 응답으로 2번 값 3 반영 → 요청 1의 응답 5가 늦게 옴
    const reported = applyUnread(NO_UNREAD, 2, 3);
    assert.deepEqual(applyUnread(reported, 1, 5), reported);
    assert.deepEqual(applyUnread(reported, 3, 4), { count: 4, applied: 3 });
  });

  test('로그아웃 — 값을 지우고 그 전에 보낸 요청의 응답은 버린다', () => {
    const before = applyUnread(NO_UNREAD, 1, 7);
    const cleared = clearUnread(3);
    assert.equal(cleared.count, null);
    assert.equal(badgeText(cleared.count), null);
    assert.deepEqual(applyUnread(cleared, 2, 9), cleared);
    assert.notDeepEqual(cleared, before);
    // 다시 로그인한 뒤 요청은 반영
    assert.deepEqual(applyUnread(cleared, 4, 2), { count: 2, applied: 4 });
  });
});

/** 손으로 시간을 넘기는 타이머 */
function fakeClock() {
  let now = 0;
  let seq = 0;
  const tasks = new Map<number, { at: number; run: () => void }>();
  const timers: Timers = {
    set: (run, ms) => {
      seq += 1;
      tasks.set(seq, { at: now + ms, run });
      return seq;
    },
    clear: (id) => {
      tasks.delete(id as number);
    },
  };
  const advance = (ms: number) => {
    now += ms;
    for (const [id, task] of [...tasks].sort((a, b) => a[1].at - b[1].at)) {
      if (task.at <= now && tasks.has(id)) {
        tasks.delete(id);
        task.run();
      }
    }
  };
  return { timers, advance };
}

describe('쓰기 요청 뒤 배지 다시 받기', () => {
  test('쓰기(POST · PUT · PATCH · DELETE)만, 읽음 · 모두 읽음 · 배지 자신 · 로그인 · 로그아웃은 뺀다', () => {
    assert.equal(refreshAfterWrite('POST', '/events'), true);
    assert.equal(refreshAfterWrite('PATCH', '/settings/email'), true);
    assert.equal(refreshAfterWrite('POST', '/reports/3/email'), true);
    assert.equal(refreshAfterWrite('PUT', '/anything'), true);
    assert.equal(refreshAfterWrite('DELETE', '/anything'), true);
    assert.equal(refreshAfterWrite('post', '/events'), true);
    assert.equal(refreshAfterWrite('GET', '/events'), false);
    assert.equal(refreshAfterWrite('GET', '/notifications/unread-count'), false);
    assert.equal(refreshAfterWrite('PATCH', '/notifications/12/read'), false);
    assert.equal(refreshAfterWrite('PATCH', '/notifications/read-all'), false);
    assert.equal(refreshAfterWrite('POST', '/auth/login'), false);
    assert.equal(refreshAfterWrite('POST', '/auth/logout'), false);
    assert.equal(refreshAfterWrite('PATCH', '/notifications/read-all?x=1'), false);
  });

  test('이벤트 신청만 한 번 더(응답 뒤 배너 업로드가 끝나야 알림이 생긴다)', () => {
    assert.equal(followUpAfterWrite('POST', '/events'), true);
    assert.equal(followUpAfterWrite('PATCH', '/settings/email'), false);
    assert.equal(followUpAfterWrite('GET', '/events'), false);
  });

  test('쓰기 뒤 300ms에 refresh, GET · 제외 경로 뒤에는 부르지 않는다', () => {
    const clock = fakeClock();
    let calls = 0;
    const writes = createWriteRefresher(() => (calls += 1), () => true, clock.timers);
    writes.onResponse('GET', '/events');
    writes.onResponse('PATCH', '/notifications/7/read');
    writes.onResponse('PATCH', '/notifications/read-all');
    writes.onResponse('POST', '/auth/logout');
    clock.advance(5_000);
    assert.equal(calls, 0);

    writes.onResponse('PATCH', '/settings/email');
    clock.advance(299);
    assert.equal(calls, 0);
    clock.advance(1);
    assert.equal(calls, 1);
  });

  test('몰린 쓰기는 300ms 안이면 한 번으로 묶고, 그 뒤 쓰기는 다시 한 번', () => {
    const clock = fakeClock();
    let calls = 0;
    const writes = createWriteRefresher(() => (calls += 1), () => true, clock.timers);
    writes.onResponse('PATCH', '/settings/email');
    clock.advance(100);
    writes.onResponse('POST', '/reports/3/email');
    clock.advance(150);
    writes.onResponse('PATCH', '/settings/email');
    clock.advance(50);
    assert.equal(calls, 1);
    writes.onResponse('PATCH', '/settings/email');
    clock.advance(300);
    assert.equal(calls, 2);
  });

  test('이벤트 신청은 300ms 뒤와 3초 뒤 두 번', () => {
    const clock = fakeClock();
    let calls = 0;
    const writes = createWriteRefresher(() => (calls += 1), () => true, clock.timers);
    writes.onResponse('POST', '/events');
    clock.advance(300);
    assert.equal(calls, 1);
    clock.advance(2_700);
    assert.equal(calls, 2);
  });

  test('로그아웃 상태면 묶지도 부르지도 않고, 그만두면(cancel) 기다리던 것도 버린다', () => {
    const clock = fakeClock();
    let calls = 0;
    let live = false;
    const writes = createWriteRefresher(() => (calls += 1), () => live, clock.timers);
    writes.onResponse('POST', '/events');
    clock.advance(5_000);
    assert.equal(calls, 0);

    live = true;
    writes.onResponse('POST', '/events');
    writes.cancel();
    clock.advance(5_000);
    assert.equal(calls, 0);
  });

  test('createBatch — 첫 요청 뒤 delay에 한 번', () => {
    const clock = fakeClock();
    let calls = 0;
    const batch = createBatch(() => (calls += 1), 300, clock.timers);
    batch.request();
    batch.request();
    clock.advance(300);
    batch.request();
    clock.advance(300);
    assert.equal(calls, 2);
  });
});
