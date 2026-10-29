import type { Tone } from '@/theme/tokens';
import type { EventMonitoring, MonitoringEventItem, MonitoringEventList, MonitoringPhase, MonitoringSeries, MonitoringService, MonitoringStep, MonitoringStepKey } from '@/types';
import { formatNumber } from '../../utils/format.ts';

/**
 * UI-TP-11 모니터링 표시 규칙(명세 1 · 2 · 4 · 5 · 7절) — 화면과 분리, node --test 대상.
 * 상태 → 문구, 단계 표시, 예상 대기 문구, 칩 색, 시각 형식, 그래프 데이터를 여기에만 둔다.
 * 이 모듈과 이 모듈이 부르는 파일은 상대 경로 + .ts로 import한다(Node가 @/ 를 풀지 못함).
 * 시각은 서버 기준: 응답 serverTime + 받은 뒤 흐른 시간. 브라우저 시계는 쓰지 않는다.
 */

/** 값 없음 — Portal 공통(결과 탭 · EmptyValue와 같은 기호) */
export const EMPTY = '-';

/** 자동 갱신 간격(목록 · 현황) */
export const POLL_INTERVAL_MS = 15_000;

/* ---------- 문구 ---------- */

export const TEXT = {
  noEvent: '지금 볼 수 있는 이벤트가 없어요',
  noEventDescription: '이벤트 당일 입장 시각부터 이곳에서 실시간 현황을 볼 수 있어요.',
  beforeEntry: '입장 시각부터 데이터가 보여요',
  checkingTitle: '운영팀이 확인하고 있어요',
  prepareFailed: '운영팀이 입장 준비를 확인하고 있어요',
  unavailable: '운영팀이 이벤트 준비를 확인하고 있어요',
  unavailableDescription: '확인이 끝나면 이곳에서 실시간 현황을 볼 수 있어요.',
  checkingItem: '확인 중',
  ended: '이벤트가 끝났어요',
  endedDescription: '확정 결과는 이벤트 내역 › 결과 탭에서 볼 수 있어요.',
  serviceDelayed: '요청이 몰려 처리가 늦어지고 있어요. 운영팀이 확인 중이에요',
  rejectsDescription: '입장 토큰(JWT) 확인에 실패한 수 (서명 오류·만료·재사용) · 늘어나면 사전 가이드의 토큰 구성과 서버 시각을 확인해 주세요',
  waitingDescription: '번호표를 받고 순서를 기다리는 고객',
} as const;

/* ---------- 시각 ---------- */

const msOf = (iso: string) => new Date(iso).getTime();

/** 시각(ms) → 한국 시각 ISO('YYYY-MM-DDTHH:mm:ss+09:00') — 서버 일시와 같은 형식이라 글자를 잘라 쓴다 */
export const kstIso = (ms: number) => `${new Date(ms + 9 * 3_600_000).toISOString().slice(0, 19)}+09:00`;

/** 지금(서버 기준 추정) = 마지막 응답의 serverTime + 그 응답을 받은 뒤 흐른 시간 */
export const estimateNow = (serverTime: string, elapsedMs: number) => msOf(serverTime) + Math.max(0, elapsedMs);

/** 오늘(nowMs의 한국 날짜)이면 'HH:mm', 아니면 'MM-DD HH:mm' */
export function clockOf(iso: string | null | undefined, nowMs: number) {
  if (!iso) return EMPTY;
  return iso.slice(0, 10) === kstIso(nowMs).slice(0, 10) ? iso.slice(11, 16) : `${iso.slice(5, 10)} ${iso.slice(11, 16)}`;
}

/** 'HH:mm:ss' — 마지막 갱신 */
export const secondsClock = (iso: string) => iso.slice(11, 19);

/** 이벤트 기간: 시작 'MM-DD HH:mm', 끝은 같은 날이면 'HH:mm' */
export function periodText(startAt: string, endAt: string) {
  const end = startAt.slice(0, 10) === endAt.slice(0, 10) ? endAt.slice(11, 16) : `${endAt.slice(5, 10)} ${endAt.slice(11, 16)}`;
  return `${startAt.slice(5, 10)} ${startAt.slice(11, 16)} ~ ${end}`;
}

/** 남은 시간: 60분 미만 '27분 46초', 60분 이상 '2시간 5분' */
export function durationText(seconds: number) {
  const s = Math.max(0, Math.floor(seconds));
  if (s >= 3600) {
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    return m === 0 ? `${h}시간` : `${h}시간 ${m}분`;
  }
  const m = Math.floor(s / 60);
  return m === 0 ? `${s % 60}초` : `${m}분 ${s % 60}초`;
}

/* ---------- 값 ---------- */

/** 개수 — null은 '-', 0은 '0' */
export const countText = (n: number | null | undefined, unit = '') => (n == null ? EMPTY : `${formatNumber(n)}${unit}`);

/** 막대 비율 0~1 — 모르거나 분모가 0이면 0 */
export function share(part: number | null | undefined, total: number | null | undefined) {
  if (part == null || total == null || total <= 0) return 0;
  return Math.min(1, Math.max(0, part / total));
}

/** '64%' — 모르면 '-' */
export const shareText = (part: number | null | undefined, total: number | null | undefined) =>
  part == null || total == null || total <= 0 ? EMPTY : `${Math.round(share(part, total) * 100)}%`;

/** 예상 대기 문구. 시작 전 · 품절 뒤는 상태 문구, 그 밖 waitMinutes(0 = 1분 이내) */
export function waitText(state: MonitoringPhase, waitMinutes: number | null) {
  if (state === 'ENTRY_OPEN') return '시작 전';
  if (state === 'SOLD_OUT') return '품절 안내 중';
  if (waitMinutes == null) return EMPTY;
  return waitMinutes === 0 ? '1분 이내' : `약 ${formatNumber(waitMinutes)}분`;
}

/* ---------- 진행 단계 카드 ---------- */

/** 카드 · 그래프 · 연동 확인을 그리는 상태(그 밖은 안내만) */
export const showsMetrics = (m: EventMonitoring) =>
  !m.prepareFailed && (m.state === 'ENTRY_OPEN' || m.state === 'LIVE' || m.state === 'SOLD_OUT');

/**
 * 확인 중 — 실패했거나 예정 시각이 지났는데 진행되지 않은 이벤트. 이벤트 내역(eventChipsOf)과 같은 기준을 현황 응답으로 본다:
 * 열리지 않음(배포 · 호스팅 시작 실패) · 준비 실패 · 실패한 단계(입장 · 결과 집계) · 예정 시각이 지났는데 아직인 단계(호스팅 시작 · 입장)
 */
export const isCheckingMonitoring = (m: EventMonitoring) =>
  m.state === 'UNAVAILABLE' || m.prepareFailed || m.steps.some((s) => s.status === 'FAILED' || s.delayed);

/** 카드 · 그래프 자리(입장 전 등)의 안내 — 확인 중이면 운영팀 안내(황토) */
export const placeholderOf = (m: EventMonitoring): { title: string; tone: Tone } =>
  isCheckingMonitoring(m) ? { title: TEXT.checkingTitle, tone: 'amber' } : { title: TEXT.beforeEntry, tone: 'grey' };

export interface Headline {
  label: string;
  value: string;
  /** 값 옆 보조 문구 (예: '14:00 입장') */
  note?: string;
  /** 확인 중 안내 — 남은 시간 대신 '확인 중 · 입장 예정 HH:mm' 한 줄(황토) */
  delayed?: boolean;
}

/** 윗줄 왼쪽: 입장까지 · 시작까지 · 종료까지 · 종료 — 종료는 이벤트 종료(end_at, 발급 마감) */
export function headlineOf(m: EventMonitoring, nowMs: number): Headline {
  const until = (iso: string) => durationText((msOf(iso) - nowMs) / 1000);
  const p = m.period;
  switch (m.state) {
    case 'BEFORE_ENTRY':
      // 준비 실패로 입장 시각이 지났으면 남은 시간 대신 확인 중(입장 시각 전이면 평소 문구)
      return entryDelayed(m, nowMs)
        ? { label: '확인 중', value: `입장 예정 ${clockOf(p.entryAt, nowMs)}`, delayed: true }
        : { label: '입장까지', value: until(p.entryAt), note: `${clockOf(p.entryAt, nowMs)} 입장` };
    case 'ENTRY_OPEN':
      return { label: '시작까지', value: until(p.startAt), note: `${clockOf(p.startAt, nowMs)} 시작` };
    case 'LIVE':
    case 'SOLD_OUT':
      return { label: '종료까지', value: until(p.endAt) };
    case 'ENDED':
      return { label: '종료', value: clockOf(p.endAt, nowMs) };
    case 'UNAVAILABLE':
      // 화면은 진행 단계 카드 대신 빈 상태만 그린다
      return { label: TEXT.checkingItem, value: EMPTY };
  }
}

/** 준비 실패로 입장 시각이 지났는데 아직 입장 전 */
export const entryDelayed = (m: EventMonitoring, nowMs: number) =>
  m.state === 'BEFORE_ENTRY' && m.prepareFailed && nowMs >= msOf(m.period.entryAt);

/** 윗줄 '재고 HH:mm 품절' — 품절일 때만 */
export const soldOutNote = (m: EventMonitoring, nowMs: number) =>
  m.state === 'SOLD_OUT' && m.header.soldOutAt ? `재고 ${clockOf(m.header.soldOutAt, nowMs)} 품절` : null;

export const STEP_TITLE: Record<MonitoringStepKey, string> = {
  HOSTING_START: '호스팅 시작',
  ENTRY: '입장',
  LIVE: '이벤트 진행',
  END: '이벤트 종료',
  AGGREGATE: '결과 집계',
};

/** 둘 중 늦은 시각 — 입장은 OPEN(준비)이 입장 시각보다 먼저여도 고객 입장은 입장 시각부터 */
const laterOf = (a: string | null, b: string | null) => (a == null ? b : b == null || msOf(a) > msOf(b) ? a : b);

/**
 * 단계 아래 문구. 끝남 'HH:mm 완료', 진행 중 'HH:mm 시작 · 진행 중', 아직 'HH:mm 예정'(api가 정한 상태대로 — 기다리는 구간은 예정).
 * 호스팅 시작 완료 시각은 실제 시각(없으면 예정 시각), 입장은 입장 시각과 실제(OPEN) 시각 중 늦은 쪽.
 * 이벤트 진행: 품절이면 'HH:mm 시작 · HH:mm 품절', 끝나면 'HH:mm 시작 · HH:mm 종료'(end_at).
 * 결과 집계: 'HH:mm 이후 집계'(hosting_end_at) → '집계 중' → '완료'.
 * 실패(입장 · 결과 집계)와 예정 시각이 지났는데 아직인 단계는 '확인 중', 열리지 않은 채 끝난 이벤트의 뒤 단계는 '진행 안 됨'.
 */
export function stepCaption(step: MonitoringStep, m: EventMonitoring, nowMs: number) {
  if (step.status === 'SKIPPED') return '진행 안 됨';
  if (step.status === 'FAILED') return '확인 중';
  const t = clockOf(step.at, nowMs);
  if (step.step === 'AGGREGATE') {
    return step.status === 'DONE' ? '완료' : step.status === 'CURRENT' ? '집계 중' : `${t} 이후 집계`;
  }
  if (step.delayed) return '확인 중';
  switch (step.step) {
    case 'HOSTING_START':
      return step.status === 'DONE' ? `${clockOf(step.actualAt ?? step.at, nowMs)} 완료` : `${t} 예정`;
    case 'ENTRY': {
      const opened = clockOf(laterOf(step.actualAt, step.at), nowMs);
      return step.status === 'DONE' ? `${opened} 완료` : step.status === 'CURRENT' ? `${opened} 시작 · 진행 중` : `${t} 예정`;
    }
    case 'LIVE': {
      const soldOut = (m.state === 'SOLD_OUT' || m.state === 'ENDED') && m.header.soldOutAt ? ` · ${clockOf(m.header.soldOutAt, nowMs)} 품절` : null;
      if (step.status === 'DONE') return `${t} 시작${soldOut ?? ` · ${clockOf(m.period.endAt, nowMs)} 종료`}`;
      return step.status === 'CURRENT' ? `${t} 시작${soldOut ?? ' · 진행 중'}` : `${t} 예정`;
    }
    case 'END':
      return step.status === 'DONE' ? `${t} 완료` : `${t} 예정`;
  }
}

/**
 * 주기 호출을 멈출 응답 — 종료 뒤 결과 집계가 끝났을 때(완료 · 실패(확인 중) · 진행 안 됨).
 * 그 전까지는 종료여도 15초마다 다시 불러 진행 단계(결과 집계)를 갱신한다(서버는 Prometheus를 읽지 않는다).
 */
export const pollingDone = (m: EventMonitoring) =>
  m.state === 'ENDED' && m.steps.some((s) => s.step === 'AGGREGATE' && s.status !== 'UPCOMING' && s.status !== 'CURRENT');

/* ---------- 칩 · 갱신 문구 ---------- */

export interface Chip {
  label: string;
  tone: Tone;
}

/**
 * 서비스 표시(명세 2절). null(입장 전 · 종료 · 준비 실패 — 판정하지 않음)이면 숨김.
 * 화면이 데이터 지연(서버 stale 또는 호출 실패)이면 마지막 판정 대신 확인 중.
 */
export function serviceChip(service: MonitoringService | null, delayed: boolean): Chip | null {
  if (service == null) return null;
  if (delayed || service === 'CHECKING') return { label: '확인 중', tone: 'grey' };
  return service === 'DELAYED' ? { label: '지연 중', tone: 'amber' } : { label: '정상', tone: 'green' };
}

/** 입장 실패 칩 — 최근 5분 0건 정상, 1건 이상 주의. 값이 없으면 숨김 */
export function rejectChip(rejects5m: number | null): Chip | null {
  if (rejects5m == null) return null;
  return rejects5m === 0 ? { label: '정상', tone: 'green' } : { label: '주의', tone: 'amber' };
}

/**
 * 윗줄 오른쪽 갱신 문구. 종료는 갱신하지 않아 없음.
 * delayed = 서버 stale 또는 화면의 호출 실패(마지막 값 유지). 마지막 시각은 마지막 정상 조회(없으면 응답 시각).
 */
export function refreshStatus(m: EventMonitoring, delayed: boolean): { text: string; delayed: boolean } | null {
  if (m.state === 'ENDED') return null;
  const last = secondsClock(m.lastUpdatedAt ?? m.serverTime);
  return delayed ? { text: `확인 중 · 마지막 ${last}`, delayed } : { text: `15초마다 자동 갱신 · 마지막 ${last}`, delayed };
}

/* ---------- 드롭다운 ---------- */

/** 항목 끝 상태: '진행 중' · '입장 중' · '오늘 18:00 시작' · '종료 10-24' */
export function itemCaption(item: MonitoringEventItem, nowMs: number) {
  if (item.group === 'LIVE') return item.state === 'ENTRY_OPEN' ? '입장 중' : '진행 중';
  if (item.group === 'TODAY') {
    const sameDay = item.startAt.slice(0, 10) === kstIso(nowMs).slice(0, 10);
    return `${sameDay ? `오늘 ${item.startAt.slice(11, 16)}` : clockOf(item.startAt, nowMs)} 시작`;
  }
  return `종료 ${item.endAt.slice(5, 10)}`;
}

/**
 * 처음 고를 이벤트: ?eventId=가 있으면 목록에 없어도 그것(열리지 않은 이벤트 · 지난 이벤트를 URL로 직접 — 서버가 정한다.
 * 없거나 권한 없음은 404 → 화면이 ?eventId=를 지우고 다시 고른다), 아니면 defaultEventId
 */
export function pickEventId(list: MonitoringEventList, raw: string | null): number | null {
  if (raw && /^\d{1,10}$/.test(raw)) return Number(raw);
  return list.defaultEventId;
}

/** 드롭다운 항목 하나 */
export interface EventOption {
  eventId: number;
  label: string;
}

/** 현황 응답 → 항목 끝 상태(목록에 없는 이벤트를 보고 있을 때). 확인 중인 이벤트(열리지 않음 포함)는 '확인 중' */
export function detailCaption(m: EventMonitoring, nowMs: number) {
  if (isCheckingMonitoring(m)) return TEXT.checkingItem;
  const group = m.state === 'ENDED' ? 'ENDED' : m.state === 'BEFORE_ENTRY' ? 'TODAY' : 'LIVE';
  const p = m.period;
  return itemCaption(
    { eventId: m.eventId, name: m.name, group, state: m.state, prepareFailed: m.prepareFailed, entryAt: p.entryAt, startAt: p.startAt, endAt: p.endAt },
    nowMs,
  );
}

/**
 * 드롭다운 항목: 목록 + 보고 있는 이벤트가 목록에 없으면(URL로 직접 · 종료 7일 경과 · 보는 중 열리지 않게 됨) 현황 응답으로 맨 위에 하나.
 * 확인 중이면 시각 문구('입장 중' 등) 대신 '확인 중' — 보고 있는 이벤트는 현황 응답으로, 나머지는 checkingIds
 * (목록 응답에 호스팅 · 이벤트 상태가 없어 화면이 이벤트 내역 목록에 eventChipsOf로 정한 값).
 */
export function selectOptions(
  events: MonitoringEventItem[],
  selected: number | null,
  m: EventMonitoring | undefined,
  nowMs: number,
  checkingIds: ReadonlySet<number> = new Set(),
): EventOption[] {
  const viewing = m && m.eventId === selected ? m : null;
  const checking = (eventId: number) => (viewing?.eventId === eventId ? isCheckingMonitoring(viewing) : checkingIds.has(eventId));
  const options = events.map((e) => ({
    eventId: e.eventId,
    label: `${e.name} · ${checking(e.eventId) ? TEXT.checkingItem : itemCaption(e, nowMs)}`,
  }));
  if (viewing && !events.some((e) => e.eventId === viewing.eventId)) {
    options.unshift({ eventId: viewing.eventId, label: `${viewing.name} · ${detailCaption(viewing, nowMs)}` });
  }
  return options;
}

/**
 * 경계 시각(입장 · 시작 · 종료 end_at)을 넘었으면 다시 부를 경계 키. 경계 하나당 한 번만(fired에 넣는다) —
 * 다시 불러도 서버 시각이 아직 경계 전이면 상태가 그대로라 또 부르지 않고 15초 주기로 돌아간다.
 * 마지막 응답의 serverTime이 이미 지난 경계는 해당 없음(그 응답이 이미 반영).
 */
export function boundaryToRefetch(m: EventMonitoring, nowMs: number, fired: ReadonlySet<string>): string | null {
  // 종료 뒤 결과 집계(hosting_end_at)는 15초 주기로 · 열리지 않은 이벤트는 시각으로 상태가 바뀌지 않는다(회복만 확인)
  if (m.state === 'ENDED' || m.state === 'UNAVAILABLE') return null;
  const server = msOf(m.serverTime);
  for (const at of [m.period.entryAt, m.period.startAt, m.period.endAt]) {
    const key = `${m.eventId}@${at}`;
    if (server < msOf(at) && msOf(at) <= nowMs && !fired.has(key)) return key;
  }
  return null;
}

/* ---------- 그래프 ---------- */

const MINUTE = 60_000;

/**
 * 분당 입장 · 발급 결과 부제: '최근 1분: 입장 612명 · 발급 성공 630건' (품절 뒤 '· 품절 N건').
 * 최근 1분 = 다 찬 마지막 칸 — 마지막 칸은 지금 분이라 아직 차는 중(asOf = 조회 시각). 다 찬 칸이 없으면 마지막 칸
 */
export function perMinuteSubtitle(series: MonitoringSeries, state: MonitoringPhase, asOf: string) {
  const full = series.perMinute.filter((p) => msOf(p.t) + MINUTE <= msOf(asOf));
  const last = full[full.length - 1] ?? series.perMinute[series.perMinute.length - 1];
  if (!last) return null;
  const soldOut = state === 'SOLD_OUT' ? ` · 품절 ${countText(last.soldOut, '건')}` : '';
  return `최근 1분: 입장 ${countText(last.entries, '명')} · 발급 성공 ${countText(last.success, '건')}${soldOut}`;
}

/** 가로축 글자 간격(분) — 글자가 약 8개 이하가 되는 가장 작은 정각 간격. 글자는 이 간격의 배수 분에만 */
export function tickMinutes(points: number) {
  return [5, 10, 15, 30, 60, 120, 180].find((step) => points / step <= 8) ?? 360;
}

/** 이 칸(ms)에 가로축 글자를 붙이는지 — 정각 간격의 배수 분(한국 시각) */
export const isTick = (ms: number, step: number) => Math.floor((ms + 9 * 3_600_000) / MINUTE) % step === 0;

/** 두 그래프(분당 · 남은 재고)가 같이 쓰는 가로축 — 같은 1분 칸이라 가로 범위 · 눈금 · 기준선 위치가 맞는다 */
export interface ChartAxis {
  /** 1분 칸 시작 시각(ms). 입장 칸 ~ 지금 칸, 소진 예상 점선이 있으면 그 분까지 늘린다 */
  minutes: number[];
  /** 가로축 글자 간격(분) */
  tick: number;
  /** 소진 예상 칸(점선 끝) — 그리지 않으면 null */
  projectionIndex: number | null;
}

/** 소진 예상은 진행 중이고 이벤트 종료(end_at) 전에 소진될 때만, 지금 칸보다 뒤면 그린다 — 종료 뒤 예상까지 늘리면 막대가 눌린다 */
export function chartAxis(series: MonitoringSeries, state: MonitoringPhase, endAt: string): ChartAxis {
  const minutes = series.perMinute.map((p) => msOf(p.t));
  const last = minutes[minutes.length - 1];
  let projectionIndex: number | null = null;
  if (state === 'LIVE' && series.projection && last != null && msOf(series.projection.at) <= msOf(endAt)) {
    const end = Math.floor(msOf(series.projection.at) / MINUTE) * MINUTE;
    for (let t = last + MINUTE; t <= end; t += MINUTE) minutes.push(t);
    if (end > last) projectionIndex = minutes.length - 1;
  }
  return { minutes, tick: tickMinutes(minutes.length), projectionIndex };
}

/** 칸 값을 가로축 길이로 — 소진 예상까지 늘린 칸은 null */
export const onAxis = <T>(values: (T | null)[], axis: ChartAxis): (T | null)[] => axis.minutes.map((_, i) => values[i] ?? null);

/** 이 시각이 든 칸(ms) — 기준선(입장 · 시작) 위치. 범위 밖이면 null */
export function minuteSlot(axis: ChartAxis, iso: string) {
  const minute = Math.floor(msOf(iso) / MINUTE) * MINUTE;
  return axis.minutes.includes(minute) ? minute : null;
}

export interface StockChartData {
  /** 칸마다 남은 재고(계단). 소진 예상까지 늘린 칸은 null */
  remaining: (number | null)[];
  /** 마지막 실제 값 → 예상 칸 0 (점선). 예상이 없으면 모두 null */
  projection: (number | null)[];
  /** 품절 칸(값 0) 한 칸만 */
  soldOut: (number | null)[];
  hasProjection: boolean;
  /** 마지막 실제 값의 칸(ms) — '지금' 기준선. 값이 없으면 null */
  lastActual: number | null;
}

/** 남은 재고 그래프. 진행 중이면 소진 예상 점선, 품절이면 예상 대신 품절 칸 */
export function stockChart(series: MonitoringSeries, state: MonitoringPhase, soldOutAt: string | null, axis: ChartAxis): StockChartData {
  const remaining = onAxis(
    series.stock.map((p) => p.remaining),
    axis,
  );
  const projection: (number | null)[] = remaining.map(() => null);
  const soldOut: (number | null)[] = remaining.map(() => null);
  let lastIndex = -1;
  series.stock.forEach((p, i) => {
    if (p.remaining != null) lastIndex = i;
  });
  const hasProjection = axis.projectionIndex != null && lastIndex >= 0;
  if (hasProjection) {
    projection[lastIndex] = remaining[lastIndex];
    projection[axis.projectionIndex!] = 0;
  }
  const soldOutSlot = state === 'SOLD_OUT' && soldOutAt ? minuteSlot(axis, soldOutAt) : null;
  if (soldOutSlot != null) soldOut[axis.minutes.indexOf(soldOutSlot)] = 0;
  return { remaining, projection, soldOut, hasProjection, lastActual: lastIndex >= 0 ? axis.minutes[lastIndex] : null };
}

/** 남은 재고 부제: '지금 5,460개 · 최근 5분 속도면 15:21쯤 소진' / 품절 'HH:mm 품절' */
export function stockSubtitle(m: EventMonitoring, nowMs: number) {
  if (m.state === 'SOLD_OUT') return m.header.soldOutAt ? `${clockOf(m.header.soldOutAt, nowMs)} 품절` : '품절';
  const remaining = m.kpi?.stock != null && m.kpi.issued != null ? m.kpi.stock - m.kpi.issued : null;
  const projection = m.series?.projection ? ` · 최근 5분 속도면 ${clockOf(m.series.projection.at, nowMs)}쯤 소진` : '';
  return `지금 ${countText(remaining, '개')}${projection}`;
}
