import type { Plan } from '@/types';

/**
 * 트래픽 플랜·호스팅 단가.
 * TODO: 단가는 기획 문서의 "2026.09.21 기준 계획" 값이 확정되면 API(또는 확정 값)로 교체
 */
export const planMeta: Record<Plan, { capacity: number; short: string; hourly: number }> = {
  S: { capacity: 10000, short: '1만', hourly: 2000 },
  M: { capacity: 30000, short: '3만', hourly: 5000 },
  L: { capacity: 50000, short: '5만', hourly: 8000 },
  XL: { capacity: 100000, short: '10만', hourly: 15000 },
};

export const PLANS: Plan[] = ['S', 'M', 'L', 'XL'];

/** 도메인 호스팅 시간당 단가 (UI-TP-08 Check Point 예: 시간당 1,200원) */
export const HOSTING_HOURLY = 1200;
