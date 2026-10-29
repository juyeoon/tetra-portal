import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import type { EventStatus, HostingStatus } from '../types/index.ts';
import { entryOpenAtOf } from './event.ts';
import { eventChipsOf, eventStatusMeta, eventStatusView, hostingStatusMeta, hostingStatusView } from './status.ts';

const HOSTING_START_AT = '2026-10-24T11:00:00+09:00';
const START_AT = '2026-10-24T13:00:00+09:00';
/** 입장 시각 = 시작 − ENTRY_OPEN_LEAD_MINUTES (리드타임이 바뀌어도 테스트는 그대로) */
const ENTRY_MS = new Date(entryOpenAtOf(START_AT)).getTime();
const ms = (iso: string) => new Date(iso).getTime();
const BEFORE_ALL = ms('2026-10-23T09:00:00+09:00');

const CHECKING = { label: '확인 중', tone: 'amber' };
const view = (hosting: HostingStatus | null, event: EventStatus | null, nowMs: number) => eventStatusView(hosting, event, START_AT, nowMs);

describe('eventStatusView: 이벤트 칸', () => {
  test('승인 전(null)은 null', () => {
    assert.equal(view(null, null, BEFORE_ALL), null);
  });

  test('배포 실패는 오픈 예정이 아니라 확인 중 — 입장 시각 전에도', () => {
    // 배포가 실패하면 WF2 · WF4 스케줄이 없어 SCHEDULED가 그대로 남는다
    assert.deepEqual(view('DEPLOY_FAILED', 'SCHEDULED', BEFORE_ALL), CHECKING);
    assert.deepEqual(view('DEPLOY_FAILED', 'SCHEDULED', ms('2026-10-30T00:00:00+09:00')), CHECKING);
    // 스케줄이 일부 만들어져 입장이 열렸어도 확인 중
    assert.deepEqual(view('DEPLOY_FAILED', 'OPEN', ENTRY_MS), CHECKING);
  });

  test('호스팅 시작 · 종료 실패, 이벤트 준비 실패는 이벤트 상태와 상관없이 확인 중', () => {
    assert.deepEqual(view('HOSTING_FAILED', 'SCHEDULED', BEFORE_ALL), CHECKING);
    assert.deepEqual(view('HOSTING_FAILED', 'OPEN', ENTRY_MS), CHECKING);
    assert.deepEqual(view('END_FAILED', 'OPEN', ms('2026-10-24T16:00:00+09:00')), CHECKING);
    assert.deepEqual(view('END_FAILED', 'EXPIRED', ms('2026-10-24T16:00:00+09:00')), CHECKING);
    assert.deepEqual(view('HOSTING', 'OPEN_FAILED', ENTRY_MS - 1), CHECKING);
  });

  test('실패 기록이 없어도 오픈 예정인 채 입장 시각이 지났으면 확인 중 — 입장 시각 1ms 전까지는 오픈 예정', () => {
    assert.deepEqual(view('HOSTING', 'SCHEDULED', ENTRY_MS - 1), eventStatusMeta.SCHEDULED);
    assert.deepEqual(view('HOSTING', 'SCHEDULED', ENTRY_MS), CHECKING);
    assert.deepEqual(view('DEPLOYED', 'SCHEDULED', ENTRY_MS + 60_000), CHECKING);
  });

  test('정상 흐름은 상태 그대로', () => {
    assert.deepEqual(view('PENDING', 'SCHEDULED', BEFORE_ALL), { label: '오픈 예정', tone: 'blue' });
    assert.deepEqual(view('HOSTING', 'OPEN', ENTRY_MS), { label: '진행 중', tone: 'green' });
    assert.deepEqual(view('ENDED', 'EXPIRED', ms('2026-10-24T16:00:00+09:00')), { label: '만료', tone: 'grey' });
  });
});

describe('hostingStatusView: 호스팅 칸', () => {
  const hosting = (status: HostingStatus | null, nowMs: number) => hostingStatusView(status, HOSTING_START_AT, nowMs);

  test('승인 전(null)은 null', () => {
    assert.equal(hosting(null, BEFORE_ALL), null);
  });

  test('실패 3종은 확인 중', () => {
    for (const status of ['DEPLOY_FAILED', 'HOSTING_FAILED', 'END_FAILED'] as const) {
      assert.deepEqual(hosting(status, BEFORE_ALL), CHECKING, status);
    }
  });

  test('배포 대기 · 배포 완료인 채 호스팅 시작 시각이 지나면 확인 중 — 1ms 전까지는 그대로', () => {
    const start = ms(HOSTING_START_AT);
    assert.deepEqual(hosting('PENDING', start - 1), { label: '배포 대기', tone: 'amber' });
    assert.deepEqual(hosting('DEPLOYED', start - 1), { label: '배포 완료', tone: 'blue' });
    assert.deepEqual(hosting('PENDING', start), CHECKING);
    assert.deepEqual(hosting('DEPLOYED', start), CHECKING);
  });

  test('호스팅 중 · 호스팅 종료는 시각과 상관없이 그대로', () => {
    assert.deepEqual(hosting('HOSTING', ms('2026-10-24T12:00:00+09:00')), { label: '호스팅 중', tone: 'green' });
    assert.deepEqual(hosting('ENDED', ms('2026-10-24T16:00:00+09:00')), { label: '호스팅 종료', tone: 'grey' });
  });
});

describe('eventChipsOf: 목록 · 상세 상단', () => {
  const chips = (hostingStatus: HostingStatus | null, eventStatus: EventStatus | null, nowMs: number) =>
    eventChipsOf({ hostingStatus, eventStatus }, { startAt: START_AT, hostingStartAt: HOSTING_START_AT }, nowMs);

  test('배포 실패 — 두 칸 모두 확인 중', () => {
    assert.deepEqual(chips('DEPLOY_FAILED', 'SCHEDULED', BEFORE_ALL), { hosting: CHECKING, event: CHECKING, checking: true });
  });

  test('한 칸만 확인 중이어도 checking', () => {
    assert.deepEqual(chips('HOSTING', 'OPEN_FAILED', ENTRY_MS), { hosting: hostingStatusMeta.HOSTING, event: CHECKING, checking: true });
  });

  test('정상 · 승인 전은 checking 아님', () => {
    assert.equal(chips('HOSTING', 'OPEN', ENTRY_MS).checking, false);
    assert.deepEqual(chips(null, null, BEFORE_ALL), { hosting: null, event: null, checking: false });
  });
});

describe('고객 칩 문구', () => {
  // 실패 대신 확인 중(2026-10-29) — 빨강 · 실패 문구를 쓰지 않는다
  test('호스팅 · 이벤트 상태 칩에 빨강 · 실패 · 오류 · 지연이 없다', () => {
    for (const meta of [...Object.values(hostingStatusMeta), ...Object.values(eventStatusMeta)]) {
      assert.notEqual(meta.tone, 'red', meta.label);
      assert.doesNotMatch(meta.label, /실패|오류|지연/);
    }
  });
});
