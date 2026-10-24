import type { Plan } from '@/types';

/** 10000 → '1만', 15000 → '1.5만', 5000 → '5,000' */
export function formatCapacity(n: number) {
  if (n < 10000) return n.toLocaleString('ko-KR');
  const man = n / 10000;
  return `${Number.isInteger(man) ? man : man.toFixed(1)}만`;
}

/** 'M 플랜 (3만 명)' — 요금제 목록을 아직 못 받았으면 코드만 */
export function planLabel(planCode: string, plans?: Plan[]) {
  const plan = plans?.find((p) => p.planCode === planCode);
  return plan ? `${planCode} 플랜 (${formatCapacity(plan.maxCapacity)} 명)` : `${planCode} 플랜`;
}

/** 'YYYY-MM-DDTHH:mm'(datetime-local 입력값, 한국 시각) → API 일시 'YYYY-MM-DDTHH:mm:00+09:00' */
export const toApiDateTime = (local: string) => `${local}:00+09:00`;

/** 시각(ms) → API 일시 형식(+09:00) — formatDateTime이 글자를 잘라 쓰므로 계산한 시각도 한국 시각 문자열로 둔다 */
const toKstIso = (ms: number) => `${new Date(ms + 9 * 3_600_000).toISOString().slice(0, 19)}+09:00`;

/**
 * 입장(대기열) 열림 = 이벤트 시작 − 이 값(분). 상세 API에 입장 시각이 없어 화면이 계산한다.
 * ops-workflows wf1-2/EventRegister.asl.json Init의 entryOpenAt(start_at − 60분),
 * issuance-api tetra.session.entry-open-before(values-*.yml 60m)와 같은 값이어야 한다.
 */
export const ENTRY_OPEN_LEAD_MINUTES = 60;

/**
 * 최대 동시 세션 계산에 쓰는 세션 유효 시간(시간). Portal API에 없어 화면 상수로 둔다.
 * 출처: issuance-api values/values-dev.yml session.ttl (2h) — 바뀌면 같이 바꾼다.
 */
export const SESSION_TTL_HOURS = 2;

/** 결과 탭 "최대 동시 접속자" 제목 옆 ⓘ 툴팁 — 세션 기준 값이라는 설명 (Console과 같은 문구) */
export const PEAK_CONCURRENT_HELP = `입장 후 ${SESSION_TTL_HOURS}시간 동안 유효한 세션 수의 최댓값입니다. 창을 닫아도 세션이 끝날 때까지 포함됩니다.`;

/** 입장 시각(+09:00) = 이벤트 시작 − ENTRY_OPEN_LEAD_MINUTES */
export function entryOpenAtOf(startAt: string): string {
  return toKstIso(new Date(startAt).getTime() - ENTRY_OPEN_LEAD_MINUTES * 60_000);
}
