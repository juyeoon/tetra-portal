import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import type { EventMonitoring, MonitoringEventItem, MonitoringEventList, MonitoringSeries, MonitoringStep } from '../../types/index.ts';
import {
  EMPTY,
  boundaryToRefetch,
  chartAxis,
  clockOf,
  countText,
  durationText,
  estimateNow,
  detailCaption,
  headlineOf,
  isCheckingMonitoring,
  isTick,
  itemCaption,
  kstIso,
  minuteSlot,
  onAxis,
  periodText,
  perMinuteSubtitle,
  pickEventId,
  placeholderOf,
  pollingDone,
  refreshStatus,
  rejectChip,
  selectOptions,
  serviceChip,
  share,
  shareText,
  showsMetrics,
  soldOutNote,
  stepCaption,
  stockChart,
  stockSubtitle,
  TEXT,
  tickMinutes,
  waitText,
} from './monitoringView.ts';

const ms = (iso: string) => new Date(iso).getTime();
const NOW = '2026-10-27T15:12:14+09:00';

/** 진행 중 응답(시안 숫자). 입장 14:00 · 시작 15:00 · 종료(end_at) 15:40 · 호스팅 종료 15:45 */
function monitoring(over: Partial<EventMonitoring> = {}): EventMonitoring {
  return {
    eventId: 7,
    name: '가을 감사 쿠폰 이벤트',
    state: 'LIVE',
    prepareFailed: false,
    stale: false,
    lastUpdatedAt: NOW,
    serverTime: NOW,
    period: {
      hostingStartAt: '2026-10-27T13:20:00+09:00',
      entryAt: '2026-10-27T14:00:00+09:00',
      startAt: '2026-10-27T15:00:00+09:00',
      endAt: '2026-10-27T15:40:00+09:00',
      hostingEndAt: '2026-10-27T15:45:00+09:00',
      domain: 'event-7.tetra.io',
    },
    steps: [],
    header: { endsInSec: 1666, soldOutAt: null, service: 'NORMAL' },
    kpi: {
      issued: 9540,
      stock: 15000,
      soldOutCount: 0,
      waiting: 2400,
      waitMinutes: 4,
      activeSessions: 6820,
      planCapacity: 30000,
      planName: 'M',
      peak: { value: 7410, at: '2026-10-27T15:01:00+09:00' },
    },
    series: { perMinute: [], stock: [], projection: { at: '2026-10-27T15:21:00+09:00' } },
    rejects5m: 0,
    ...over,
  };
}

const step = (s: MonitoringStep['step'], status: MonitoringStep['status'], at: string | null, over: Partial<MonitoringStep> = {}): MonitoringStep => ({
  step: s,
  status,
  at,
  actualAt: null,
  delayed: false,
  ...over,
});

describe('시각 형식', () => {
  test('오늘이면 HH:mm, 아니면 MM-DD HH:mm (한국 날짜 기준)', () => {
    assert.equal(clockOf('2026-10-27T15:40:00+09:00', ms(NOW)), '15:40');
    assert.equal(clockOf('2026-10-24T18:00:00+09:00', ms(NOW)), '10-24 18:00');
    assert.equal(clockOf(null, ms(NOW)), EMPTY);
  });

  test('자정 직후 — 브라우저 시간대가 아니라 한국 날짜로 오늘을 정한다', () => {
    const justAfterMidnight = ms('2026-10-28T00:05:00+09:00'); // UTC로는 10-27 15:05
    assert.equal(clockOf('2026-10-27T23:50:00+09:00', justAfterMidnight), '10-27 23:50');
    assert.equal(clockOf('2026-10-28T00:01:00+09:00', justAfterMidnight), '00:01');
  });

  test('이벤트 기간: 끝이 같은 날이면 시각만', () => {
    assert.equal(periodText('2026-10-27T15:00:00+09:00', '2026-10-27T15:40:00+09:00'), '10-27 15:00 ~ 15:40');
    assert.equal(periodText('2026-10-27T23:00:00+09:00', '2026-10-28T01:00:00+09:00'), '10-27 23:00 ~ 10-28 01:00');
  });

  test('남은 시간: 60분 미만 분·초, 60분 이상 시간·분, 지난 시각은 0초', () => {
    assert.equal(durationText(59), '59초');
    assert.equal(durationText(27 * 60 + 46), '27분 46초');
    assert.equal(durationText(59 * 60 + 59.9), '59분 59초');
    assert.equal(durationText(3600), '1시간');
    assert.equal(durationText(2 * 3600 + 5 * 60 + 30), '2시간 5분');
    assert.equal(durationText(-5), '0초');
  });

  test('지금 = serverTime + 받은 뒤 흐른 시간(브라우저 시계 아님)', () => {
    assert.equal(estimateNow(NOW, 2500), ms(NOW) + 2500);
    assert.equal(estimateNow(NOW, -10), ms(NOW));
  });
});

describe('값 표시', () => {
  test('null은 -, 0은 0, 천 단위 쉼표', () => {
    assert.equal(countText(null), '-');
    assert.equal(countText(0), '0');
    assert.equal(countText(9540), '9,540');
    assert.equal(countText(2400, '명'), '2,400명');
    assert.equal(countText(null, '명'), '-');
  });

  test('막대 비율 — 모르거나 분모 0이면 0, 1을 넘지 않는다', () => {
    assert.equal(share(9540, 15000), 0.636);
    assert.equal(share(null, 15000), 0);
    assert.equal(share(10, 0), 0);
    assert.equal(share(40000, 30000), 1);
    assert.equal(shareText(9540, 15000), '64%');
    assert.equal(shareText(6820, null), '-');
  });

  test('예상 대기 문구', () => {
    assert.equal(waitText('LIVE', 4), '약 4분');
    assert.equal(waitText('LIVE', 0), '1분 이내');
    assert.equal(waitText('LIVE', null), '-');
    assert.equal(waitText('ENTRY_OPEN', null), '시작 전');
    assert.equal(waitText('SOLD_OUT', null), '품절 안내 중');
  });
});

describe('진행 단계 카드', () => {
  test('상태별 윗줄', () => {
    assert.deepEqual(headlineOf(monitoring({ state: 'BEFORE_ENTRY' }), ms('2026-10-27T13:28:00+09:00')), { label: '입장까지', value: '32분 0초', note: '14:00 입장' });
    assert.deepEqual(headlineOf(monitoring({ state: 'BEFORE_ENTRY' }), ms('2026-10-27T09:00:00+09:00')), { label: '입장까지', value: '5시간', note: '14:00 입장' });
    assert.deepEqual(headlineOf(monitoring({ state: 'ENTRY_OPEN' }), ms('2026-10-27T14:42:00+09:00')), { label: '시작까지', value: '18분 0초', note: '15:00 시작' });
    assert.deepEqual(headlineOf(monitoring(), ms(NOW)), { label: '종료까지', value: '27분 46초' });
    assert.deepEqual(headlineOf(monitoring({ state: 'SOLD_OUT' }), ms(NOW)), { label: '종료까지', value: '27분 46초' });
    assert.deepEqual(headlineOf(monitoring({ state: 'ENDED' }), ms('2026-10-28T09:00:00+09:00')), { label: '종료', value: '10-27 15:40' });
  });

  test('준비 실패로 입장 시각이 지났으면 확인 중 · 입장 예정, 지나기 전이면 평소 문구', () => {
    const failed = monitoring({ state: 'BEFORE_ENTRY', prepareFailed: true });
    assert.deepEqual(headlineOf(failed, ms('2026-10-27T14:20:00+09:00')), { label: '확인 중', value: '입장 예정 14:00', delayed: true });
    assert.deepEqual(headlineOf(failed, ms('2026-10-27T14:00:00+09:00')), { label: '확인 중', value: '입장 예정 14:00', delayed: true });
    assert.deepEqual(headlineOf(failed, ms('2026-10-27T13:59:00+09:00')), { label: '입장까지', value: '1분 0초', note: '14:00 입장' });
  });

  test('품절이면 재고 품절 시각', () => {
    const sold = monitoring({ state: 'SOLD_OUT', header: { endsInSec: 1, soldOutAt: '2026-10-27T15:09:00+09:00', service: 'NORMAL' } });
    assert.equal(soldOutNote(sold, ms(NOW)), '재고 15:09 품절');
    assert.equal(soldOutNote(monitoring(), ms(NOW)), null);
  });

  test('단계 아래 문구 — 끝남 완료 · 진행 중 · 아직 예정', () => {
    const m = monitoring();
    assert.equal(stepCaption(step('HOSTING_START', 'DONE', m.period.hostingStartAt), m, ms(NOW)), '13:20 완료');
    assert.equal(stepCaption(step('LIVE', 'CURRENT', m.period.startAt), m, ms(NOW)), '15:00 시작 · 진행 중');
    assert.equal(stepCaption(step('END', 'UPCOMING', m.period.endAt), m, ms(NOW)), '15:40 예정');
  });

  test('기다리는 구간(예정 시각 전)은 회색 예정 — 호스팅 시작 전 · 입장 전', () => {
    const before = monitoring({ state: 'BEFORE_ENTRY' });
    assert.equal(stepCaption(step('HOSTING_START', 'UPCOMING', before.period.hostingStartAt), before, ms('2026-10-27T13:00:00+09:00')), '13:20 예정');
    assert.equal(stepCaption(step('HOSTING_START', 'DONE', before.period.hostingStartAt), before, ms('2026-10-27T13:28:00+09:00')), '13:20 완료');
    assert.equal(stepCaption(step('ENTRY', 'UPCOMING', before.period.entryAt), before, ms('2026-10-27T13:28:00+09:00')), '14:00 예정');
    // OPEN(준비)이 입장 시각보다 먼저여도 입장 전에는 예정
    assert.equal(stepCaption(step('ENTRY', 'UPCOMING', before.period.entryAt, { actualAt: '2026-10-27T13:45:00+09:00' }), before, ms('2026-10-27T13:50:00+09:00')), '14:00 예정');
  });

  test('입장 ~ 시작은 입장 진행 중 — 시각은 입장 시각과 OPEN 시각 중 늦은 쪽', () => {
    const open = monitoring({ state: 'ENTRY_OPEN' });
    const now = ms('2026-10-27T14:42:00+09:00');
    assert.equal(stepCaption(step('ENTRY', 'CURRENT', open.period.entryAt), open, now), '14:00 시작 · 진행 중');
    // 준비(OPEN)가 먼저 — 고객 입장은 입장 시각부터
    assert.equal(stepCaption(step('ENTRY', 'CURRENT', open.period.entryAt, { actualAt: '2026-10-27T13:45:00+09:00' }), open, now), '14:00 시작 · 진행 중');
    // OPEN이 늦었으면 그 시각부터
    assert.equal(stepCaption(step('ENTRY', 'CURRENT', open.period.entryAt, { actualAt: '2026-10-27T14:01:10+09:00' }), open, now), '14:01 시작 · 진행 중');
    assert.equal(stepCaption(step('ENTRY', 'DONE', open.period.entryAt, { actualAt: '2026-10-27T13:45:00+09:00' }), monitoring(), ms(NOW)), '14:00 완료');
    assert.equal(stepCaption(step('ENTRY', 'DONE', open.period.entryAt, { actualAt: '2026-10-27T14:01:10+09:00' }), monitoring(), ms(NOW)), '14:01 완료');
  });

  test('호스팅 시작 완료 시각은 실제 시각(없으면 예정 시각)', () => {
    const open = monitoring({ state: 'ENTRY_OPEN' });
    const now = ms('2026-10-27T14:42:00+09:00');
    assert.equal(stepCaption(step('HOSTING_START', 'DONE', open.period.hostingStartAt, { actualAt: '2026-10-27T13:21:30+09:00' }), open, now), '13:21 완료');
    assert.equal(stepCaption(step('HOSTING_START', 'DONE', open.period.hostingStartAt), open, now), '13:20 완료');
  });

  test('예정 시각이 지났는데 아직이면 확인 중', () => {
    const before = monitoring({ state: 'BEFORE_ENTRY' });
    const now = ms('2026-10-27T13:25:00+09:00');
    assert.equal(stepCaption(step('HOSTING_START', 'CURRENT', before.period.hostingStartAt, { delayed: true }), before, now), '확인 중');
    const open = monitoring({ state: 'ENTRY_OPEN' });
    assert.equal(stepCaption(step('ENTRY', 'CURRENT', open.period.entryAt, { delayed: true }), open, ms('2026-10-27T14:05:00+09:00')), '확인 중');
  });

  test('입장 실패는 확인 중 — 입장 시각 전후 · 종료 뒤 모두', () => {
    const failed = monitoring({ state: 'BEFORE_ENTRY', prepareFailed: true });
    const entry = step('ENTRY', 'FAILED', failed.period.entryAt);
    assert.equal(stepCaption(entry, failed, ms('2026-10-27T14:20:00+09:00')), '확인 중');
    assert.equal(stepCaption(entry, failed, ms('2026-10-27T13:50:00+09:00')), '확인 중');
    assert.equal(stepCaption(entry, monitoring({ state: 'ENDED' }), ms('2026-10-27T16:00:00+09:00')), '확인 중');
  });

  test('이벤트 진행 — 진행 중 · 품절 시각, 종료 뒤 시작 · 종료(end_at) 또는 품절', () => {
    const sold = monitoring({ state: 'SOLD_OUT', header: { endsInSec: 1, soldOutAt: '2026-10-27T15:09:00+09:00', service: 'NORMAL' } });
    assert.equal(stepCaption(step('LIVE', 'CURRENT', sold.period.startAt), sold, ms(NOW)), '15:00 시작 · 15:09 품절');
    const ended = monitoring({ state: 'ENDED', header: { endsInSec: null, soldOutAt: null, service: null } });
    const after = ms('2026-10-27T15:42:00+09:00');
    assert.equal(stepCaption(step('LIVE', 'DONE', ended.period.startAt), ended, after), '15:00 시작 · 15:40 종료');
    const endedSold = monitoring({ state: 'ENDED', header: { endsInSec: null, soldOutAt: '2026-10-27T15:09:00+09:00', service: null } });
    assert.equal(stepCaption(step('LIVE', 'DONE', endedSold.period.startAt), endedSold, after), '15:00 시작 · 15:09 품절');
  });

  test('이벤트 종료는 end_at — 전에는 예정, 지나면 완료(종료 처리 중 없음)', () => {
    const ended = monitoring({ state: 'ENDED' });
    assert.equal(stepCaption(step('END', 'UPCOMING', ended.period.endAt), monitoring(), ms(NOW)), '15:40 예정');
    assert.equal(stepCaption(step('END', 'DONE', ended.period.endAt), ended, ms('2026-10-27T15:42:00+09:00')), '15:40 완료');
  });

  test('결과 집계 — 호스팅 종료 전 이후 집계 · WF4 · 집계 중 · 완료, 실패는 확인 중', () => {
    const ended = monitoring({ state: 'ENDED' });
    const now = ms('2026-10-27T15:42:00+09:00');
    assert.equal(stepCaption(step('AGGREGATE', 'UPCOMING', ended.period.hostingEndAt), monitoring(), ms(NOW)), '15:45 이후 집계');
    assert.equal(stepCaption(step('AGGREGATE', 'UPCOMING', ended.period.hostingEndAt), ended, now), '15:45 이후 집계');
    assert.equal(stepCaption(step('AGGREGATE', 'CURRENT', ended.period.hostingEndAt), ended, now), '집계 중');
    assert.equal(stepCaption(step('AGGREGATE', 'DONE', ended.period.hostingEndAt), ended, now), '완료');
    assert.equal(stepCaption(step('AGGREGATE', 'FAILED', ended.period.hostingEndAt), ended, now), '확인 중');
  });

  test('열리지 않은 채 끝난 이벤트의 진행 · 종료 · 결과 집계는 진행 안 됨(집계 중으로 남지 않는다)', () => {
    const ended = monitoring({ state: 'ENDED' });
    const now = ms('2026-10-27T16:00:00+09:00');
    assert.equal(stepCaption(step('LIVE', 'SKIPPED', ended.period.startAt), ended, now), '진행 안 됨');
    assert.equal(stepCaption(step('END', 'SKIPPED', ended.period.endAt), ended, now), '진행 안 됨');
    assert.equal(stepCaption(step('AGGREGATE', 'SKIPPED', ended.period.hostingEndAt), ended, now), '진행 안 됨');
  });

  test('다른 날 단계 시각은 날짜를 붙인다', () => {
    const m = monitoring({ state: 'ENDED' });
    assert.equal(stepCaption(step('END', 'DONE', m.period.endAt), m, ms('2026-10-28T09:00:00+09:00')), '10-27 15:40 완료');
  });

  test('카드 · 그래프는 입장 ~ 종료 전만, 준비 실패는 안내만', () => {
    assert.equal(showsMetrics(monitoring()), true);
    assert.equal(showsMetrics(monitoring({ state: 'ENTRY_OPEN' })), true);
    assert.equal(showsMetrics(monitoring({ state: 'SOLD_OUT' })), true);
    assert.equal(showsMetrics(monitoring({ state: 'BEFORE_ENTRY' })), false);
    assert.equal(showsMetrics(monitoring({ state: 'ENDED' })), false);
    assert.equal(showsMetrics(monitoring({ state: 'BEFORE_ENTRY', prepareFailed: true })), false);
    assert.equal(showsMetrics(monitoring({ state: 'UNAVAILABLE' })), false);
  });
});

describe('칩 · 갱신 문구', () => {
  test('서비스 — 정상 초록 · 지연 중 황토 · 확인 중 회색, null이면 숨김', () => {
    assert.deepEqual(serviceChip('NORMAL', false), { label: '정상', tone: 'green' });
    assert.deepEqual(serviceChip('DELAYED', false), { label: '지연 중', tone: 'amber' });
    assert.deepEqual(serviceChip('CHECKING', false), { label: '확인 중', tone: 'grey' });
    assert.equal(serviceChip(null, false), null);
  });

  test('데이터 지연이면 마지막 판정 대신 확인 중 (빨강 없음)', () => {
    assert.deepEqual(serviceChip('DELAYED', true), { label: '확인 중', tone: 'grey' });
    assert.deepEqual(serviceChip('NORMAL', true), { label: '확인 중', tone: 'grey' });
    assert.equal(serviceChip(null, true), null);
  });

  test('입장 실패 — 0건 정상, 1건 이상 주의, 없으면 숨김', () => {
    assert.deepEqual(rejectChip(0), { label: '정상', tone: 'green' });
    assert.deepEqual(rejectChip(3), { label: '주의', tone: 'amber' });
    assert.equal(rejectChip(null), null);
  });

  test('갱신 문구 — 평소 · 데이터 지연은 확인 중(마지막 정상 조회 시각) · 종료는 없음', () => {
    assert.deepEqual(refreshStatus(monitoring(), false), { text: '15초마다 자동 갱신 · 마지막 15:12:14', delayed: false });
    assert.deepEqual(refreshStatus(monitoring({ stale: true, lastUpdatedAt: '2026-10-27T15:11:44+09:00' }), true), {
      text: '확인 중 · 마지막 15:11:44',
      delayed: true,
    });
    // 입장 전은 Prometheus를 읽지 않아 lastUpdatedAt이 없다 → 응답 시각
    assert.deepEqual(refreshStatus(monitoring({ state: 'BEFORE_ENTRY', lastUpdatedAt: null, serverTime: '2026-10-27T13:28:00+09:00' }), false), {
      text: '15초마다 자동 갱신 · 마지막 13:28:00',
      delayed: false,
    });
    assert.equal(refreshStatus(monitoring({ state: 'ENDED' }), false), null);
  });
});

describe('드롭다운', () => {
  const item = (over: Partial<MonitoringEventItem>): MonitoringEventItem => ({
    eventId: 7,
    name: '가을 감사',
    group: 'LIVE',
    state: 'LIVE',
    prepareFailed: false,
    entryAt: '2026-10-27T14:00:00+09:00',
    startAt: '2026-10-27T15:00:00+09:00',
    endAt: '2026-10-27T15:40:00+09:00',
    ...over,
  });

  test('항목 끝 상태 문구', () => {
    assert.equal(itemCaption(item({}), ms(NOW)), '진행 중');
    assert.equal(itemCaption(item({ state: 'ENTRY_OPEN' }), ms(NOW)), '입장 중');
    assert.equal(itemCaption(item({ group: 'TODAY', state: 'BEFORE_ENTRY', startAt: '2026-10-27T18:00:00+09:00' }), ms(NOW)), '오늘 18:00 시작');
    assert.equal(itemCaption(item({ group: 'ENDED', state: 'ENDED', endAt: '2026-10-24T20:00:00+09:00' }), ms(NOW)), '종료 10-24');
  });

  const list: MonitoringEventList = { events: [item({ eventId: 7 }), item({ eventId: 9, group: 'ENDED' })], defaultEventId: 7 };

  test('?eventId=가 있으면 목록에 없어도 그것(서버가 정한다), 없거나 형식이 틀리면 기본 선택', () => {
    assert.equal(pickEventId(list, '9'), 9);
    assert.equal(pickEventId(list, '12'), 12); // 열리지 않은 이벤트 · 지난 이벤트 — 없으면 404로 다시 고른다
    assert.equal(pickEventId(list, 'abc'), 7);
    assert.equal(pickEventId(list, null), 7);
    assert.equal(pickEventId({ events: [], defaultEventId: null }, '7'), 7);
    assert.equal(pickEventId({ events: [], defaultEventId: null }, null), null);
  });

  const unavailable = monitoring({ eventId: 12, name: '겨울 이벤트', state: 'UNAVAILABLE', steps: [], kpi: null, series: null });

  test('목록에 없는 이벤트를 보고 있으면 현황 응답으로 맨 위에 항목 — 열리지 않은 이벤트는 확인 중', () => {
    assert.deepEqual(selectOptions(list.events, 12, unavailable, ms(NOW)), [
      { eventId: 12, label: '겨울 이벤트 · 확인 중' },
      { eventId: 7, label: '가을 감사 · 진행 중' },
      { eventId: 9, label: '가을 감사 · 종료 10-27' },
    ]);
    // 응답 전(또는 다른 이벤트 응답)이면 목록 그대로
    assert.equal(selectOptions(list.events, 12, undefined, ms(NOW)).length, 2);
    assert.equal(selectOptions(list.events, 12, monitoring({ eventId: 7 }), ms(NOW)).length, 2);
  });

  test('보는 중 열리지 않게 됐는데 목록이 아직 옛 값이면 그 항목만 확인 중', () => {
    const now7 = monitoring({ eventId: 7, state: 'UNAVAILABLE' });
    assert.deepEqual(selectOptions(list.events, 7, now7, ms(NOW)).map((o) => o.label), ['가을 감사 · 확인 중', '가을 감사 · 종료 10-27']);
  });

  test('목록에서 빠진 지난 이벤트는 현황 상태로 문구', () => {
    const old = monitoring({ eventId: 30, name: '여름 이벤트', state: 'ENDED', period: { ...monitoring().period, endAt: '2026-10-10T15:40:00+09:00' } });
    assert.equal(detailCaption(old, ms(NOW)), '종료 10-10');
    assert.equal(detailCaption(monitoring({ state: 'ENTRY_OPEN' }), ms(NOW)), '입장 중');
    assert.equal(detailCaption(unavailable, ms(NOW)), '확인 중');
  });

  test('확인 중이면 시각 문구 대신 확인 중 — 보고 있는 이벤트는 현황 응답, 나머지는 이벤트 내역 판정(checkingIds)', () => {
    const events = [
      item({ eventId: 2, name: '입장 시각 지남', state: 'ENTRY_OPEN' }),
      item({ eventId: 4, name: '입장 준비', group: 'TODAY', state: 'BEFORE_ENTRY', prepareFailed: true, startAt: '2026-10-27T18:00:00+09:00' }),
      item({ eventId: 7 }),
    ];
    const labels = (selected: number, m: EventMonitoring, checkingIds?: ReadonlySet<number>) =>
      selectOptions(events, selected, m, ms(NOW), checkingIds).map((o) => o.label);
    // 입장 시각이 지났는데 열리지 않음(배포 완료 · 오픈 예정) — 현황의 호스팅 시작 · 입장 단계가 delayed. '입장 중'이 아니다
    const notOpened = monitoring({
      eventId: 2,
      state: 'ENTRY_OPEN',
      steps: [step('HOSTING_START', 'CURRENT', '2026-10-27T13:20:00+09:00', { delayed: true }), step('ENTRY', 'UPCOMING', '2026-10-27T14:00:00+09:00', { delayed: true })],
    });
    assert.deepEqual(labels(2, notOpened), ['입장 시각 지남 · 확인 중', '입장 준비 · 오늘 18:00 시작', '가을 감사 · 진행 중']);
    // 준비 실패를 보고 있으면 '오늘 HH:mm 시작'이 아니라 확인 중
    const prepareFailed = monitoring({ eventId: 4, state: 'BEFORE_ENTRY', prepareFailed: true, steps: [step('ENTRY', 'FAILED', '2026-10-27T14:00:00+09:00')] });
    assert.equal(labels(4, prepareFailed)[1], '입장 준비 · 확인 중');
    // 보고 있지 않은 이벤트는 checkingIds(이벤트 내역 eventChipsOf)로
    assert.deepEqual(labels(7, monitoring(), new Set([2, 4])), ['입장 시각 지남 · 확인 중', '입장 준비 · 확인 중', '가을 감사 · 진행 중']);
    // 보고 있는 이벤트는 현황 응답이 먼저 — 정상이면 checkingIds에 있어도 시각 문구
    assert.equal(labels(7, monitoring(), new Set([7]))[2], '가을 감사 · 진행 중');
    // 목록에 없는 이벤트를 보고 있을 때도 같은 판정
    assert.equal(detailCaption(prepareFailed, ms(NOW)), '확인 중');
  });
});

describe('경계 시각 즉시 재조회', () => {
  const entry = monitoring({ state: 'ENTRY_OPEN', serverTime: '2026-10-27T14:59:50+09:00' });

  test('경계 전에는 부르지 않는다', () => {
    assert.equal(boundaryToRefetch(entry, ms('2026-10-27T14:59:59+09:00'), new Set()), null);
  });

  test('시작 시각을 넘으면 그 경계를 한 번 부른다', () => {
    assert.equal(boundaryToRefetch(entry, ms('2026-10-27T15:00:00+09:00'), new Set()), '7@2026-10-27T15:00:00+09:00');
  });

  test('다시 불렀는데 서버 시각이 아직 경계 전이어도(상태 그대로) 또 부르지 않는다 — 15초 주기로', () => {
    const fired = new Set(['7@2026-10-27T15:00:00+09:00']);
    // 재조회 응답: 서버 시각 14:59:59.6 → 아직 ENTRY_OPEN
    const again = monitoring({ state: 'ENTRY_OPEN', serverTime: '2026-10-27T14:59:59+09:00' });
    assert.equal(boundaryToRefetch(again, ms('2026-10-27T15:00:01+09:00'), fired), null);
    assert.equal(boundaryToRefetch(again, ms('2026-10-27T15:00:09+09:00'), fired), null);
  });

  test('이미 지난 경계(응답이 반영한 시각)는 해당 없고, 다음 경계(종료 = end_at, 호스팅 종료 아님)는 따로 한 번', () => {
    const live = monitoring({ serverTime: '2026-10-27T15:39:55+09:00' });
    const fired = new Set(['7@2026-10-27T15:00:00+09:00']);
    assert.equal(boundaryToRefetch(live, ms('2026-10-27T15:39:58+09:00'), fired), null);
    assert.equal(boundaryToRefetch(live, ms('2026-10-27T15:40:00+09:00'), fired), '7@2026-10-27T15:40:00+09:00');
  });

  test('종료 뒤 · 열리지 않은 이벤트는 부르지 않는다(15초 주기만)', () => {
    assert.equal(boundaryToRefetch(monitoring({ state: 'ENDED', serverTime: '2026-10-27T13:00:00+09:00' }), ms(NOW), new Set()), null);
    assert.equal(boundaryToRefetch(monitoring({ state: 'UNAVAILABLE', serverTime: '2026-10-27T13:00:00+09:00' }), ms(NOW), new Set()), null);
  });
});

describe('종료 뒤 갱신', () => {
  const steps = (aggregate: MonitoringStep['status']): MonitoringStep[] => [
    step('HOSTING_START', 'DONE', null),
    step('ENTRY', 'DONE', null),
    step('LIVE', 'DONE', null),
    step('END', 'DONE', null),
    step('AGGREGATE', aggregate, null),
  ];

  test('종료여도 결과 집계가 끝날 때까지(예정 · 집계 중) 주기 호출을 계속한다', () => {
    assert.equal(pollingDone(monitoring({ state: 'ENDED', steps: steps('UPCOMING') })), false);
    assert.equal(pollingDone(monitoring({ state: 'ENDED', steps: steps('CURRENT') })), false);
  });

  test('집계가 끝나면(완료 · 실패 · 진행 안 됨) 멈춘다', () => {
    assert.equal(pollingDone(monitoring({ state: 'ENDED', steps: steps('DONE') })), true);
    assert.equal(pollingDone(monitoring({ state: 'ENDED', steps: steps('FAILED') })), true);
    assert.equal(pollingDone(monitoring({ state: 'ENDED', steps: steps('SKIPPED') })), true);
  });

  test('종료 전 · 열리지 않은 이벤트는 멈추지 않는다', () => {
    assert.equal(pollingDone(monitoring({ steps: steps('UPCOMING') })), false);
    assert.equal(pollingDone(monitoring({ state: 'UNAVAILABLE', steps: [] })), false);
  });
});

describe('그래프', () => {
  const t = (hm: string) => `2026-10-27T${hm}:00+09:00`;
  const minute = (hm: string, entries: number | null, success: number | null, soldOut: number | null) => ({ t: t(hm), entries, success, soldOut });

  test('분당 부제 — 최근 1분은 다 찬 마지막 칸(마지막 칸은 지금 분), 품절 뒤에는 품절 건수도', () => {
    const series: MonitoringSeries = {
      perMinute: [minute('15:10', 700, 640, 0), minute('15:11', 612, 630, 12), minute('15:12', 90, 80, 3)],
      stock: [],
      projection: null,
    };
    // 15:12:14 조회 — 15:12 칸은 14초 치라 15:11 칸
    assert.equal(perMinuteSubtitle(series, 'LIVE', NOW), '최근 1분: 입장 612명 · 발급 성공 630건');
    assert.equal(perMinuteSubtitle(series, 'SOLD_OUT', NOW), '최근 1분: 입장 612명 · 발급 성공 630건 · 품절 12건');
    assert.equal(perMinuteSubtitle(series, 'LIVE', t('15:13')), '최근 1분: 입장 90명 · 발급 성공 80건');
    // 다 찬 칸이 없으면 마지막 칸
    assert.equal(perMinuteSubtitle({ ...series, perMinute: [minute('15:12', null, null, null)] }, 'LIVE', NOW), '최근 1분: 입장 - · 발급 성공 -');
    assert.equal(perMinuteSubtitle({ ...series, perMinute: [] }, 'LIVE', NOW), null);
  });

  test('가로축 글자는 정각 간격 — 약 8개 이하', () => {
    assert.equal(tickMinutes(40), 5);
    assert.equal(tickMinutes(73), 10);
    assert.equal(tickMinutes(100), 15);
    assert.equal(tickMinutes(200), 30);
    assert.equal(isTick(ms(t('14:10')), 10), true);
    assert.equal(isTick(ms(t('14:11')), 10), false);
    assert.equal(isTick(ms(t('00:00')), 60), true); // 한국 자정
  });

  /** 15:00 ~ 15:02 세 칸 — 분당 · 재고가 같은 칸 */
  const series = (stock: (number | null)[], projection: string | null): MonitoringSeries => ({
    perMinute: stock.map((_, i) => minute(`15:0${i}`, 10, 10, 0)),
    stock: stock.map((v, i) => ({ t: t(`15:0${i}`), remaining: v })),
    projection: projection ? { at: projection } : null,
  });
  const END_AT = t('15:40');

  test('두 그래프는 같은 1분 칸 가로축 — 소진 예상이 있으면 그 분까지 늘린다', () => {
    const none = chartAxis(series([15000, 12000, 9000], null), 'LIVE', END_AT);
    assert.deepEqual(none.minutes, [ms(t('15:00')), ms(t('15:01')), ms(t('15:02'))]);
    assert.equal(none.projectionIndex, null);
    assert.equal(none.tick, 5);

    const projected = chartAxis(series([15000, 12000, 9000], '2026-10-27T15:05:30+09:00'), 'LIVE', END_AT);
    assert.deepEqual(projected.minutes.map((v) => kstIso(v).slice(11, 16)), ['15:00', '15:01', '15:02', '15:03', '15:04', '15:05']);
    assert.equal(projected.projectionIndex, 5);
    // 분당 막대는 늘린 칸이 비어 있다
    assert.deepEqual(onAxis([1, 2, 3], projected), [1, 2, 3, null, null, null]);
    assert.equal(minuteSlot(projected, '2026-10-27T15:01:40+09:00'), ms(t('15:01')));
    assert.equal(minuteSlot(projected, t('14:59')), null);
  });

  test('이벤트 종료 뒤에야 소진되는 예상 · 품절 · 지금 분 안의 예상은 늘리지 않는다', () => {
    assert.equal(chartAxis(series([15000, 12000, 9000], t('15:41')), 'LIVE', END_AT).minutes.length, 3);
    assert.equal(chartAxis(series([300, 0, 0], t('15:05')), 'SOLD_OUT', END_AT).minutes.length, 3);
    const thisMinute = chartAxis(series([15000, 12000, 30], '2026-10-27T15:02:40+09:00'), 'LIVE', END_AT);
    assert.equal(thisMinute.minutes.length, 3);
    assert.equal(thisMinute.projectionIndex, null);
  });

  test('소진 예상 점선 — 마지막 실제 값에서 예상 칸 0까지', () => {
    const s = series([15000, 12000, null], t('15:05'));
    const d = stockChart(s, 'LIVE', null, chartAxis(s, 'LIVE', END_AT));
    assert.equal(d.hasProjection, true);
    assert.deepEqual(d.remaining, [15000, 12000, null, null, null, null]);
    assert.deepEqual(d.projection, [null, 12000, null, null, null, 0]);
    assert.deepEqual(d.soldOut, [null, null, null, null, null, null]);
    assert.equal(d.lastActual, ms(t('15:01')));
  });

  test('예상이 없으면 점선 없음', () => {
    const s = series([15000, 12000], null);
    const d = stockChart(s, 'LIVE', null, chartAxis(s, 'LIVE', END_AT));
    assert.equal(d.hasProjection, false);
    assert.deepEqual(d.projection, [null, null]);
    assert.deepEqual(d.remaining, [15000, 12000]);
  });

  test('품절이면 예상 대신 품절 칸', () => {
    const s = series([300, 0, 0], t('15:21'));
    const d = stockChart(s, 'SOLD_OUT', t('15:01'), chartAxis(s, 'SOLD_OUT', END_AT));
    assert.equal(d.hasProjection, false);
    assert.deepEqual(d.soldOut, [null, 0, null]);
  });

  test('재고 부제', () => {
    assert.equal(stockSubtitle(monitoring({ kpi: { ...monitoring().kpi!, issued: 9540 } }), ms(NOW)), '지금 5,460개 · 최근 5분 속도면 15:21쯤 소진');
    assert.equal(stockSubtitle(monitoring({ series: { perMinute: [], stock: [], projection: null } }), ms(NOW)), '지금 5,460개');
    assert.equal(stockSubtitle(monitoring({ kpi: { ...monitoring().kpi!, issued: null } }), ms(NOW)), '지금 - · 최근 5분 속도면 15:21쯤 소진');
    assert.equal(stockSubtitle(monitoring({ state: 'SOLD_OUT', header: { endsInSec: 1, soldOutAt: t('15:09'), service: 'NORMAL' } }), ms(NOW)), '15:09 품절');
  });
});

describe('고객 문구', () => {
  // 실패 · 원인 대신 확인 중(2026-10-29). 입장 실패(JWT)는 고객사 설정 문제라 그대로 보여 준다
  test('안내 문구에 실패 · 문제 · 불가 같은 말이 없다', () => {
    for (const [key, text] of Object.entries(TEXT)) {
      if (key === 'rejectsDescription') continue;
      assert.doesNotMatch(text, /실패|오류|문제|불가|열리지 않/, key);
    }
  });
});

describe('확인 중 판정 · 카드 자리 안내', () => {
  const at = '2026-10-27T14:00:00+09:00';

  test('현황 응답으로 확인 중 — 열리지 않음 · 준비 실패 · 실패한 단계 · 예정 시각이 지났는데 아직인 단계', () => {
    assert.equal(isCheckingMonitoring(monitoring({ steps: [step('HOSTING_START', 'DONE', at), step('ENTRY', 'DONE', at), step('LIVE', 'CURRENT', at)] })), false);
    assert.equal(isCheckingMonitoring(monitoring({ state: 'UNAVAILABLE' })), true);
    assert.equal(isCheckingMonitoring(monitoring({ state: 'BEFORE_ENTRY', prepareFailed: true })), true);
    assert.equal(isCheckingMonitoring(monitoring({ state: 'BEFORE_ENTRY', steps: [step('HOSTING_START', 'CURRENT', at, { delayed: true })] })), true);
    assert.equal(isCheckingMonitoring(monitoring({ state: 'ENTRY_OPEN', steps: [step('ENTRY', 'UPCOMING', at, { delayed: true })] })), true);
    assert.equal(isCheckingMonitoring(monitoring({ state: 'ENDED', steps: [step('AGGREGATE', 'FAILED', at)] })), true);
    assert.equal(isCheckingMonitoring(monitoring({ state: 'ENDED', steps: [step('AGGREGATE', 'DONE', at)] })), false);
  });

  test('카드 자리 — 확인 중(준비 실패 등)이면 운영팀 안내(황토), 아니면 입장 시각 안내(회색)', () => {
    assert.deepEqual(placeholderOf(monitoring({ state: 'BEFORE_ENTRY', prepareFailed: true })), { title: '운영팀이 확인하고 있어요', tone: 'amber' });
    assert.deepEqual(placeholderOf(monitoring({ state: 'BEFORE_ENTRY', steps: [step('HOSTING_START', 'CURRENT', at, { delayed: true })] })), {
      title: '운영팀이 확인하고 있어요',
      tone: 'amber',
    });
    assert.deepEqual(placeholderOf(monitoring({ state: 'BEFORE_ENTRY' })), { title: '입장 시각부터 데이터가 보여요', tone: 'grey' });
  });
});
