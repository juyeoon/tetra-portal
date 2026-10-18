import { HOSTING_HOURLY, planMeta } from '@/mocks/pricing';
import type { Plan } from '@/types';

/** '2026.09.20 00:00' → Date */
export function parseDateTime(dt: string) {
  const [d, t = '00:00'] = dt.split(' ');
  const [y, m, day] = d.split('.').map(Number);
  const [hh, mm] = t.split(':').map(Number);
  return new Date(y, m - 1, day, hh, mm);
}

/** 호스팅 시간 — 시간 단위 올림 (23:59 종료는 다음 정시까지로 계산) */
export function hostingHours(start: string, end: string) {
  const ms = parseDateTime(end).getTime() - parseDateTime(start).getTime();
  return ms > 0 ? Math.ceil(ms / 3_600_000) : 0;
}

/** 예상 비용 = 호스팅 시간 × (도메인 호스팅 단가 + 플랜 시간당 단가). 추가 트래픽 요금은 별도 산정 */
export function estimateCost(plan: Plan, start: string, end: string) {
  const hours = hostingHours(start, end);
  return { hours, hosting: hours * HOSTING_HOURLY, plan: hours * planMeta[plan].hourly, total: hours * (HOSTING_HOURLY + planMeta[plan].hourly) };
}

export const planLabel = (plan: Plan) => `${plan} 플랜 (${planMeta[plan].short} 명)`;

export const totalCouponStock = (coupons: { quantity: number }[]) => coupons.reduce((sum, c) => sum + (c.quantity || 0), 0);
