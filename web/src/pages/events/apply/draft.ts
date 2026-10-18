import { defaultScreen } from '@/mocks/events';
import type { EventDraft } from '@/store/AppDataContext';
import type { BannerFile, Coupon, EventType, Plan, ScreenSetting } from '@/types';
import { isHttpUrl } from '@/utils/validation';

/** 쿠폰 행 최대 개수 (UI-TP-08 ④) */
export const MAX_COUPONS = 10;

/** 신청 단계 입력값 — 일시는 <input type="datetime-local"> 값('YYYY-MM-DDTHH:mm') 그대로 둔다 */
export interface ApplyDraft {
  type: EventType | null;
  name: string;
  eventStart: string;
  eventEnd: string;
  hostingStart: string;
  hostingEnd: string;
  banner: BannerFile | null;
  coupons: (Omit<Coupon, 'quantity'> & { quantity: string })[];
  plan: Plan | null;
  extraTraffic: boolean;
  endpointUrl: string;
  /** [확인]으로 형식 검증을 마친 URL — 입력이 바뀌면 다시 확인해야 한다 */
  verifiedUrl: string;
  screen: ScreenSetting;
}

export const emptyCoupon = () => ({ name: '', description: '', quantity: '' });

export const initialDraft = (): ApplyDraft => ({
  type: null,
  name: '',
  eventStart: '',
  eventEnd: '',
  hostingStart: '',
  hostingEnd: '',
  banner: null,
  coupons: [emptyCoupon()],
  plan: null,
  extraTraffic: false,
  endpointUrl: '',
  verifiedUrl: '',
  screen: defaultScreen,
});

/** 'YYYY-MM-DDTHH:mm' → 'YYYY.MM.DD HH:mm' */
export const toDisplayDateTime = (v: string) => (v ? `${v.slice(0, 10).replaceAll('-', '.')} ${v.slice(11, 16)}` : '');

export const parseQuantity = (v: string) => {
  const n = Number(v.replaceAll(',', ''));
  return Number.isInteger(n) && n > 0 ? n : 0;
};

/** 이벤트 정보 단계(Tab2) 입력 오류 — 키별 메시지. 비어 있으면 다음 단계로 진행 가능 */
export function infoErrors(d: ApplyDraft) {
  const e: Partial<Record<'name' | 'eventPeriod' | 'hostingPeriod' | 'banner' | 'coupons' | 'plan' | 'endpointUrl', string>> = {};
  if (!d.name.trim()) e.name = '이벤트 이름을 입력해주세요.';
  if (!d.eventStart || !d.eventEnd) e.eventPeriod = '이벤트 시작·종료 일시를 입력해주세요.';
  else if (d.eventEnd <= d.eventStart) e.eventPeriod = '종료 일시는 시작 일시보다 뒤여야 합니다.';
  if (!d.hostingStart || !d.hostingEnd) e.hostingPeriod = '도메인 호스팅 시작·종료 일시를 입력해주세요.';
  else if (d.hostingEnd <= d.hostingStart) e.hostingPeriod = '종료 일시는 시작 일시보다 뒤여야 합니다.';
  else if (d.eventStart && d.eventEnd && (d.hostingStart > d.eventStart || d.hostingEnd < d.eventEnd)) e.hostingPeriod = '호스팅 기간은 이벤트 기간을 모두 포함해야 합니다.';
  if (!d.banner) e.banner = '이벤트 대표 배너 이미지를 업로드해주세요.';
  if (d.type === 'COUPON' && d.coupons.some((c) => !c.name.trim() || !c.description.trim() || !parseQuantity(c.quantity))) {
    e.coupons = '쿠폰 이름, Description, 발급 매수(1 이상)를 모두 입력해주세요.';
  }
  if (!d.plan) e.plan = '트래픽 수용 인원 플랜을 선택해주세요.';
  if (!isHttpUrl(d.endpointUrl)) e.endpointUrl = '올바른 URL 형식이 아닙니다. (예: https://shop.example.com/tetra-entry)';
  else if (d.verifiedUrl !== d.endpointUrl) e.endpointUrl = '[확인] 버튼으로 URL 형식을 확인해주세요.';
  return e;
}

export function toEventDraft(d: ApplyDraft): EventDraft {
  return {
    name: d.name.trim(),
    type: d.type ?? 'COUPON',
    eventStart: toDisplayDateTime(d.eventStart),
    eventEnd: toDisplayDateTime(d.eventEnd),
    hostingStart: toDisplayDateTime(d.hostingStart),
    hostingEnd: toDisplayDateTime(d.hostingEnd),
    banner: d.banner ?? { name: '', width: 0, height: 0 },
    plan: d.plan ?? 'S',
    extraTraffic: d.extraTraffic,
    endpointUrl: d.endpointUrl.trim(),
    coupons: d.type === 'COUPON' ? d.coupons.map((c) => ({ name: c.name.trim(), description: c.description.trim(), quantity: parseQuantity(c.quantity) })) : [],
    screen: d.screen,
  };
}
