import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { infoErrors, initialDraft, type ApplyDraft } from './draft.ts';

/** 기준 시각 2026-11-01 09:00 (한국 시각) */
const NOW = new Date('2026-11-01T09:00:00+09:00');

/** 모든 항목이 맞는 신청 — 이벤트 11-02 10:00~12:00, 호스팅 08:00~14:00 */
function valid(patch: Partial<ApplyDraft> = {}): ApplyDraft {
  return {
    ...initialDraft(),
    type: 'COUPON',
    name: '가을 쿠폰',
    eventStart: '2026-11-02T10:00',
    eventEnd: '2026-11-02T12:00',
    hostingStart: '2026-11-02T08:00',
    hostingEnd: '2026-11-02T14:00',
    banner: {} as File,
    coupons: [{ name: '10% 할인', description: '전 상품' }],
    stock: '100',
    plan: 'M',
    endpointUrl: 'https://shop.example.com/event',
    verifiedUrl: 'https://shop.example.com/event',
    ...patch,
  };
}

describe('infoErrors: 호스팅 시작 ≤ 이벤트 시작 − 100분', () => {
  test('99분 전 거부 · 100분 전 통과 · 101분 전 통과', () => {
    assert.equal(infoErrors(valid({ hostingStart: '2026-11-02T08:21' }), NOW).hostingPeriod, '호스팅 시작은 이벤트 시작 100분 전까지여야 합니다.');
    assert.deepEqual(infoErrors(valid({ hostingStart: '2026-11-02T08:20' }), NOW), {});
    assert.deepEqual(infoErrors(valid({ hostingStart: '2026-11-02T08:19' }), NOW), {});
  });

  test('호스팅 시작 = 이벤트 시작도 거부', () => {
    assert.equal(infoErrors(valid({ hostingStart: '2026-11-02T10:00' }), NOW).hostingPeriod, '호스팅 시작은 이벤트 시작 100분 전까지여야 합니다.');
  });
});

describe('infoErrors: 호스팅 시작 ≥ 지금 + 30분', () => {
  test('+29분 거부 · +30분 통과', () => {
    const far = { eventStart: '2026-11-01T12:00', eventEnd: '2026-11-01T13:00', hostingEnd: '2026-11-01T14:00' };
    assert.equal(infoErrors(valid({ ...far, hostingStart: '2026-11-01T09:29' }), NOW).hostingPeriod, '호스팅 시작은 지금부터 30분 이후여야 합니다.');
    assert.deepEqual(infoErrors(valid({ ...far, hostingStart: '2026-11-01T09:30' }), NOW), {});
  });

  test('브라우저 시간대와 상관없이 입력값을 한국 시각으로 읽는다', () => {
    // NOW(UTC 00:00)와 같은 순간인 한국 09:00 + 30분
    assert.equal(infoErrors(valid({ eventStart: '2026-11-01T12:00', eventEnd: '2026-11-01T13:00', hostingStart: '2026-11-01T09:30' }), new Date('2026-11-01T00:00:00Z')).hostingPeriod, undefined);
  });
});

describe('infoErrors: 그 밖의 규칙', () => {
  test('이벤트 시작이 지금보다 앞이면 거부', () => {
    assert.equal(infoErrors(valid({ eventStart: '2026-11-01T08:00', eventEnd: '2026-11-01T12:00' }), NOW).eventPeriod, '이벤트 시작은 지금보다 뒤여야 합니다.');
  });

  test('호스팅 기간이 이벤트 종료를 덮지 않으면 거부', () => {
    assert.equal(infoErrors(valid({ hostingEnd: '2026-11-02T11:00' }), NOW).hostingPeriod, '호스팅 기간은 이벤트 기간을 모두 포함해야 합니다.');
  });

  test('이름 공백 · URL 공백 · http(s) 아님', () => {
    assert.equal(infoErrors(valid({ name: '   ' }), NOW).name, '이벤트 이름을 입력해주세요.');
    assert.ok(infoErrors(valid({ endpointUrl: '  ', verifiedUrl: '' }), NOW).endpointUrl);
    assert.ok(infoErrors(valid({ endpointUrl: 'ftp://shop.example.com', verifiedUrl: 'ftp://shop.example.com' }), NOW).endpointUrl);
  });
});
