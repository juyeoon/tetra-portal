import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import type { EventMonitoring, MonitoringEventItem, MonitoringEventList, MonitoringSeries, MonitoringStep } from '../../types/index.ts';
import {
  EMPTY,
  boundaryToRefetch,
  clockOf,
  countText,
  durationText,
  estimateNow,
  headlineOf,
  isTick,
  itemCaption,
  periodText,
  perMinuteSubtitle,
  pickEventId,
  refreshStatus,
  rejectChip,
  serviceChip,
  share,
  shareText,
  showsMetrics,
  soldOutNote,
  stepCaption,
  stockChart,
  stockSubtitle,
  tickMinutes,
  timeTicks,
  waitText,
} from './monitoringView.ts';

const ms = (iso: string) => new Date(iso).getTime();
const NOW = '2026-10-27T15:12:14+09:00';

/** 진행 중 응답(시안 숫자). 입장 14:00 · 시작 15:00 · 종료(hosting_end_at) 15:40 */
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
      hostingEndAt: '2026-10-27T15:40:00+09:00',
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

const step = (s: MonitoringStep['step'], status: MonitoringStep['status'], at: string | null): MonitoringStep => ({ step: s, status, at });

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

  test('준비 실패로 입장 시각이 지났으면 입장 지연 · 예정 시각, 지나기 전이면 평소 문구', () => {
    const failed = monitoring({ state: 'BEFORE_ENTRY', prepareFailed: true });
    assert.deepEqual(headlineOf(failed, ms('2026-10-27T14:20:00+09:00')), { label: '입장 지연', value: '예정 시각 14:00', delayed: true });
    assert.deepEqual(headlineOf(failed, ms('2026-10-27T14:00:00+09:00')), { label: '입장 지연', value: '예정 시각 14:00', delayed: true });
    assert.deepEqual(headlineOf(failed, ms('2026-10-27T13:59:00+09:00')), { label: '입장까지', value: '1분 0초', note: '14:00 입장' });
  });

  test('품절이면 재고 품절 시각', () => {
    const sold = monitoring({ state: 'SOLD_OUT', header: { endsInSec: 1, soldOutAt: '2026-10-27T15:09:00+09:00', service: 'NORMAL' } });
    assert.equal(soldOutNote(sold, ms(NOW)), '재고 15:09 품절');
    assert.equal(soldOutNote(monitoring(), ms(NOW)), null);
  });

  test('단계 아래 문구 — 완료 · 시작 · 진행 중 · 예정', () => {
    const m = monitoring();
    assert.equal(stepCaption(step('HOSTING_START', 'DONE', m.period.hostingStartAt), m, ms(NOW)), '13:20 완료');
    assert.equal(stepCaption(step('LIVE', 'CURRENT', m.period.startAt), m, ms(NOW)), '15:00 시작 · 진행 중');
    assert.equal(stepCaption(step('END', 'UPCOMING', m.period.hostingEndAt), m, ms(NOW)), '15:40 예정');
  });

  test('호스팅 시작 전에는 호스팅 시작이 현재 단계여도 문구는 예정', () => {
    const before = monitoring({ state: 'BEFORE_ENTRY' });
    assert.equal(stepCaption(step('HOSTING_START', 'CURRENT', before.period.hostingStartAt), before, ms('2026-10-27T13:00:00+09:00')), '13:20 예정');
  });

  test('입장 전에는 입장이 현재 단계여도 문구는 예정', () => {
    const before = monitoring({ state: 'BEFORE_ENTRY' });
    assert.equal(stepCaption(step('ENTRY', 'CURRENT', before.period.entryAt), before, ms('2026-10-27T13:28:00+09:00')), '14:00 예정');
    assert.equal(stepCaption(step('HOSTING_START', 'DONE', before.period.hostingStartAt), before, ms('2026-10-27T13:28:00+09:00')), '13:20 완료');
    // 입장 ~ 시작은 그대로 진행 중
    const open = monitoring({ state: 'ENTRY_OPEN' });
    assert.equal(stepCaption(step('ENTRY', 'CURRENT', open.period.entryAt), open, ms('2026-10-27T14:42:00+09:00')), '14:00 시작 · 진행 중');
  });

  test('준비 실패로 입장 시각이 지났으면 입장 단계는 준비 중, 지나기 전이면 예정', () => {
    const failed = monitoring({ state: 'BEFORE_ENTRY', prepareFailed: true });
    const entry = step('ENTRY', 'CURRENT', failed.period.entryAt);
    assert.equal(stepCaption(entry, failed, ms('2026-10-27T14:20:00+09:00')), '준비 중');
    assert.equal(stepCaption(entry, failed, ms('2026-10-27T13:50:00+09:00')), '14:00 예정');
  });

  test('품절이면 진행 단계에 품절 시각', () => {
    const sold = monitoring({ state: 'SOLD_OUT', header: { endsInSec: 1, soldOutAt: '2026-10-27T15:09:00+09:00', service: 'NORMAL' } });
    assert.equal(stepCaption(step('LIVE', 'CURRENT', sold.period.startAt), sold, ms(NOW)), '15:00 시작 · 15:09 품절');
  });

  test('결과 집계 — 종료 뒤 · 집계 중 · 완료', () => {
    const m = monitoring({ state: 'ENDED' });
    assert.equal(stepCaption(step('AGGREGATE', 'UPCOMING', null), monitoring(), ms(NOW)), '종료 뒤');
    assert.equal(stepCaption(step('AGGREGATE', 'CURRENT', null), m, ms(NOW)), '집계 중');
    assert.equal(stepCaption(step('AGGREGATE', 'DONE', null), m, ms(NOW)), '완료');
  });

  test('다른 날 단계 시각은 날짜를 붙인다', () => {
    const m = monitoring({ state: 'ENDED' });
    assert.equal(stepCaption(step('END', 'DONE', m.period.hostingEndAt), m, ms('2026-10-28T09:00:00+09:00')), '10-27 15:40 완료');
  });

  test('카드 · 그래프는 입장 ~ 종료 전만, 준비 실패는 안내만', () => {
    assert.equal(showsMetrics(monitoring()), true);
    assert.equal(showsMetrics(monitoring({ state: 'ENTRY_OPEN' })), true);
    assert.equal(showsMetrics(monitoring({ state: 'SOLD_OUT' })), true);
    assert.equal(showsMetrics(monitoring({ state: 'BEFORE_ENTRY' })), false);
    assert.equal(showsMetrics(monitoring({ state: 'ENDED' })), false);
    assert.equal(showsMetrics(monitoring({ state: 'BEFORE_ENTRY', prepareFailed: true })), false);
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

  test('갱신 문구 — 평소 · 데이터 지연(마지막 정상 조회 시각) · 종료는 없음', () => {
    assert.deepEqual(refreshStatus(monitoring(), false), { text: '15초마다 자동 갱신 · 마지막 15:12:14', delayed: false });
    assert.deepEqual(refreshStatus(monitoring({ stale: true, lastUpdatedAt: '2026-10-27T15:11:44+09:00' }), true), {
      text: '데이터 지연 · 마지막 15:11:44',
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
    hostingEndAt: '2026-10-27T15:40:00+09:00',
    ...over,
  });

  test('항목 끝 상태 문구', () => {
    assert.equal(itemCaption(item({}), ms(NOW)), '진행 중');
    assert.equal(itemCaption(item({ state: 'ENTRY_OPEN' }), ms(NOW)), '입장 중');
    assert.equal(itemCaption(item({ group: 'TODAY', state: 'BEFORE_ENTRY', startAt: '2026-10-27T18:00:00+09:00' }), ms(NOW)), '오늘 18:00 시작');
    assert.equal(itemCaption(item({ group: 'ENDED', state: 'ENDED', hostingEndAt: '2026-10-24T20:00:00+09:00' }), ms(NOW)), '종료 10-24');
  });

  const list: MonitoringEventList = { events: [item({ eventId: 7 }), item({ eventId: 9, group: 'ENDED' })], defaultEventId: 7 };

  test('?eventId=가 목록에 있으면 그것, 아니면 기본 선택', () => {
    assert.equal(pickEventId(list, '9'), 9);
    assert.equal(pickEventId(list, '12'), 7);
    assert.equal(pickEventId(list, 'abc'), 7);
    assert.equal(pickEventId(list, null), 7);
    assert.equal(pickEventId({ events: [], defaultEventId: null }, '7'), null);
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

  test('이미 지난 경계(응답이 반영한 시각)는 해당 없고, 다음 경계는 따로 한 번', () => {
    const live = monitoring({ serverTime: '2026-10-27T15:39:55+09:00' });
    const fired = new Set(['7@2026-10-27T15:00:00+09:00']);
    assert.equal(boundaryToRefetch(live, ms('2026-10-27T15:39:58+09:00'), fired), null);
    assert.equal(boundaryToRefetch(live, ms('2026-10-27T15:40:00+09:00'), fired), '7@2026-10-27T15:40:00+09:00');
  });

  test('종료 뒤에는 부르지 않는다', () => {
    assert.equal(boundaryToRefetch(monitoring({ state: 'ENDED', serverTime: '2026-10-27T13:00:00+09:00' }), ms(NOW), new Set()), null);
  });
});

describe('그래프', () => {
  const t = (hm: string) => `2026-10-27T${hm}:00+09:00`;

  test('분당 부제 — 최근 1분, 품절 뒤에는 품절 건수도', () => {
    const series: MonitoringSeries = {
      perMinute: [
        { t: t('15:11'), entries: 700, success: 640, soldOut: 0 },
        { t: t('15:12'), entries: 612, success: 630, soldOut: 12 },
      ],
      stock: [],
      projection: null,
    };
    assert.equal(perMinuteSubtitle(series, 'LIVE'), '최근 1분: 입장 612명 · 발급 성공 630건');
    assert.equal(perMinuteSubtitle(series, 'SOLD_OUT'), '최근 1분: 입장 612명 · 발급 성공 630건 · 품절 12건');
    assert.equal(perMinuteSubtitle({ ...series, perMinute: [{ t: t('15:12'), entries: null, success: null, soldOut: null }] }, 'LIVE'), '최근 1분: 입장 - · 발급 성공 -');
    assert.equal(perMinuteSubtitle({ ...series, perMinute: [] }, 'LIVE'), null);
  });

  test('가로축 글자는 정각 간격 — 약 8개 이하', () => {
    assert.equal(tickMinutes(40), 5);
    assert.equal(tickMinutes(73), 10);
    assert.equal(tickMinutes(100), 15);
    assert.equal(tickMinutes(200), 30);
    assert.equal(isTick(t('14:10'), 10), true);
    assert.equal(isTick(t('14:11'), 10), false);
    assert.equal(isTick(t('00:00'), 60), true); // 한국 자정
  });

  test('정각 눈금은 데이터 없는 구간(예상 점선)까지 — 한국 시각 기준', () => {
    assert.deepEqual(timeTicks(ms(t('14:03')), ms(t('14:31')), 10).map((v) => new Date(v + 9 * 3_600_000).toISOString().slice(11, 16)), ['14:10', '14:20', '14:30']);
    assert.deepEqual(timeTicks(ms(t('15:12')), ms(t('15:21')), 5).map((v) => new Date(v + 9 * 3_600_000).toISOString().slice(11, 16)), ['15:15', '15:20']);
    assert.deepEqual(timeTicks(ms(t('15:00')), ms(t('15:00')), 5), [ms(t('15:00'))]);
  });

  const stock = (values: (number | null)[]) => values.map((v, i) => ({ t: t(`15:0${i}`), remaining: v }));

  test('소진 예상 점선 — 마지막 실제 값에서 예상 시각 0까지', () => {
    const d = stockChart({ perMinute: [], stock: stock([15000, 12000, null]), projection: { at: t('15:21') } }, 'LIVE', null);
    assert.equal(d.hasProjection, true);
    assert.equal(d.x.length, 4);
    assert.equal(d.x[3], ms(t('15:21')));
    assert.deepEqual(d.remaining, [15000, 12000, null, null]);
    assert.deepEqual(d.projection, [null, 12000, null, 0]);
    assert.deepEqual(d.soldOut, [null, null, null, null]);
  });

  test('예상이 없으면 점선 없음', () => {
    const d = stockChart({ perMinute: [], stock: stock([15000, 12000]), projection: null }, 'LIVE', null);
    assert.equal(d.hasProjection, false);
    assert.deepEqual(d.projection, [null, null]);
    assert.equal(d.x.length, 2);
  });

  test('품절이면 예상 대신 품절 지점', () => {
    const d = stockChart({ perMinute: [], stock: stock([300, 0, 0]), projection: { at: t('15:21') } }, 'SOLD_OUT', t('15:01'));
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
