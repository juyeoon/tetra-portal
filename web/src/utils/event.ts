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
