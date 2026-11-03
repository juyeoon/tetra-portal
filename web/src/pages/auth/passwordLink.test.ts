import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { readPasswordLink } from './passwordLink.ts';

/** 가짜 값 — 실제 서명 아님 */
const LINK = { t: 'tenant01', exp: 1767225600, sig: 'fakeSig0' };
const QS = 't=tenant01&exp=1767225600&sig=fakeSig0';

describe('readPasswordLink: 프래그먼트 · 쿼리', () => {
  test('프래그먼트만', () => {
    assert.deepEqual(readPasswordLink(`#${QS}`, ''), LINK);
  });

  test('쿼리만', () => {
    assert.deepEqual(readPasswordLink('', `?${QS}`), LINK);
  });

  test('둘 다 있으면 프래그먼트 우선', () => {
    assert.deepEqual(readPasswordLink(`#${QS}`, '?t=other&exp=1&sig=other'), LINK);
  });

  test('빈 프래그먼트(#)는 쿼리를 읽는다', () => {
    assert.deepEqual(readPasswordLink('#', `?${QS}`), LINK);
    assert.equal(readPasswordLink('#', ''), null);
  });

  test('둘 다 없으면 null', () => {
    assert.equal(readPasswordLink('', ''), null);
  });
});

describe('readPasswordLink: 빠진 값 · 잘못된 값', () => {
  test('t · exp · sig 중 하나라도 빠지면 null', () => {
    for (const qs of ['exp=1767225600&sig=fakeSig0', 't=tenant01&sig=fakeSig0', 't=tenant01&exp=1767225600', 't=&exp=1767225600&sig=fakeSig0']) {
      assert.equal(readPasswordLink(`#${qs}`, ''), null, `#${qs}`);
      assert.equal(readPasswordLink('', `?${qs}`), null, `?${qs}`);
    }
  });

  test('exp가 양의 정수가 아니면 null', () => {
    for (const exp of ['0', '-1', '1.5', 'abc', '']) {
      assert.equal(readPasswordLink(`#t=tenant01&exp=${exp}&sig=fakeSig0`, ''), null, exp);
    }
  });

  test('프래그먼트가 불완전하면 쿼리를 읽는다 — 두 곳의 값을 섞지 않음', () => {
    assert.deepEqual(readPasswordLink('#t=other', `?${QS}`), LINK);
    assert.equal(readPasswordLink('#t=tenant01&exp=1767225600', '?sig=fakeSig0'), null);
  });
});

describe('readPasswordLink: 값 모양', () => {
  test('base64url 서명(- · _)은 그대로', () => {
    assert.deepEqual(readPasswordLink('#t=tenant01&exp=1767225600&sig=ab-cd_ef-_', ''), { ...LINK, sig: 'ab-cd_ef-_' });
    assert.deepEqual(readPasswordLink('', '?t=tenant01&exp=1767225600&sig=ab-cd_ef-_'), { ...LINK, sig: 'ab-cd_ef-_' });
  });

  test('다른 파라미터가 섞여도 셋만 읽는다', () => {
    assert.deepEqual(readPasswordLink(`#utm_source=mail&${QS}&lang=ko`, ''), LINK);
    assert.deepEqual(readPasswordLink('', `?utm_source=mail&${QS}&lang=ko`), LINK);
  });
});
