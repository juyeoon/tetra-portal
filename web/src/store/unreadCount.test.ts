import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import {
  NO_UNREAD,
  applyUnread,
  badgeText,
  badgeTitle,
  clearUnread,
  fetchOnRoute,
  navAriaLabel,
  shouldPoll,
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
