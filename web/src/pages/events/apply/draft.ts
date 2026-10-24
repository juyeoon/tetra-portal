import type { ApplyApplication } from '@/api/portal';
import type { EventType, ScreenSetting } from '@/types';
// node --test 대상 — 실행 시 불러오는 파일은 상대 경로 + .ts (Node가 @/ 를 풀지 못함)
import { toApiDateTime } from '../../../utils/event.ts';
import { isHttpUrl } from '../../../utils/validation.ts';

/** 쿠폰 행 최대 개수 (UI-TP-08 ④) */
export const MAX_COUPONS = 10;
/** 총 발급 재고 상한 (API 와 같음) */
export const MAX_STOCK = 999_999_999;
/** 배너 파일 상한 2MB · PNG / JPEG (API 와 같음) */
export const MAX_BANNER_BYTES = 2 * 1024 * 1024;
export const BANNER_TYPES = ['image/png', 'image/jpeg'];

/** 신청할 수 있는 이벤트 타입 — 템플릿이 준비된 것만 (MVP 1차는 쿠폰). 나머지는 "준비 중" */
export const AVAILABLE_TYPES: EventType[] = ['COUPON'];

export const defaultScreen: ScreenSetting = {
  title: { h: 'CENTER', v: 'TOP' },
  banner: { h: 'CENTER', v: 'TOP' },
  couponList: { h: 'CENTER', v: 'MIDDLE' },
  issueButton: { h: 'CENTER', v: 'BOTTOM' },
  theme: 'WHITE',
};

/** 신청 단계 입력값 — 일시는 <input type="datetime-local"> 값('YYYY-MM-DDTHH:mm', 한국 시각) 그대로 둔다 */
export interface ApplyDraft {
  type: EventType | null;
  name: string;
  eventStart: string;
  eventEnd: string;
  hostingStart: string;
  hostingEnd: string;
  /** 신청할 때 multipart 로 함께 보낸다 */
  banner: File | null;
  /** 배너 미리보기용 크기 (저장하지 않음) */
  bannerSize: { width: number; height: number } | null;
  coupons: { name: string; description: string }[];
  /** 총 발급 재고 — 숫자만 (쉼표 없이) */
  stock: string;
  plan: string | null;
  endpointUrl: string;
  /** [확인]으로 형식 검증을 마친 URL — 입력이 바뀌면 다시 확인해야 한다 */
  verifiedUrl: string;
  screen: ScreenSetting;
}

export const emptyCoupon = () => ({ name: '', description: '' });

export const initialDraft = (): ApplyDraft => ({
  type: null,
  name: '',
  eventStart: '',
  eventEnd: '',
  hostingStart: '',
  hostingEnd: '',
  banner: null,
  bannerSize: null,
  coupons: [emptyCoupon()],
  stock: '',
  plan: null,
  endpointUrl: '',
  verifiedUrl: '',
  screen: defaultScreen,
});

/** 'YYYY-MM-DDTHH:mm' → 'YYYY.MM.DD HH:mm' */
export const toDisplayDateTime = (v: string) => (v ? `${v.slice(0, 10).replaceAll('-', '.')} ${v.slice(11, 16)}` : '');

export const parseStock = (v: string) => {
  const n = Number(v.replaceAll(',', ''));
  return Number.isInteger(n) && n > 0 && n <= MAX_STOCK ? n : 0;
};

/**
 * 일시 규칙 상수(분) — API ApplyService와 같은 값 (Console 3-13).
 * 이벤트 오픈 준비(WF2-2) 스케줄 = 이벤트 시작 − 이 값. 출처: ops-workflows wf1-2/EventRegister.asl.json start_at − 5400000ms.
 */
export const HOSTING_LEAD_BEFORE_START = 90;
/** 호스팅 시작(WF2-1)과 이벤트 오픈 준비(WF2-2)가 동시에 돌지 않게 두는 간격(분) */
export const SCHEDULE_GAP = 10;
/** 호스팅 시작은 지금 + 이 값(분) 이후. 출처: 승인 후 WF1-2 제한 시간(EventRegister.asl.json TimeoutSeconds 1800) */
export const APPROVE_MIN_LEAD = 30;

const MINUTE = 60_000;
/** datetime-local 값(한국 시각) → ms. 브라우저 시간대와 상관없이 +09:00으로 읽는다 */
const toMs = (local: string) => new Date(toApiDateTime(local)).getTime();

export type InfoErrorKey = 'name' | 'eventPeriod' | 'hostingPeriod' | 'banner' | 'coupons' | 'stock' | 'plan' | 'endpointUrl';

/**
 * 이벤트 정보 단계(Tab2) 입력 오류 — 키별 메시지. 비어 있으면 다음 단계로 진행 가능.
 * 제출(Tab4) 직전에 다시 부르고, 서버도 같은 규칙으로 다시 검사한다.
 * @param now 기준 시각 (테스트용)
 */
export function infoErrors(d: ApplyDraft, now: Date = new Date()) {
  const e: Partial<Record<InfoErrorKey, string>> = {};
  const lead = HOSTING_LEAD_BEFORE_START + SCHEDULE_GAP;
  if (!d.name.trim()) e.name = '이벤트 이름을 입력해주세요.';
  else if (d.name.trim().length > 100) e.name = '100자 이하로 입력해주세요.';
  if (!d.eventStart || !d.eventEnd) e.eventPeriod = '이벤트 시작·종료 일시를 입력해주세요.';
  else if (d.eventEnd <= d.eventStart) e.eventPeriod = '종료 일시는 시작 일시보다 뒤여야 합니다.';
  else if (toMs(d.eventStart) <= now.getTime()) e.eventPeriod = '이벤트 시작은 지금보다 뒤여야 합니다.';
  if (!d.hostingStart || !d.hostingEnd) e.hostingPeriod = '도메인 호스팅 시작·종료 일시를 입력해주세요.';
  else if (d.hostingEnd <= d.hostingStart) e.hostingPeriod = '종료 일시는 시작 일시보다 뒤여야 합니다.';
  else if (toMs(d.hostingStart) < now.getTime() + APPROVE_MIN_LEAD * MINUTE) e.hostingPeriod = `호스팅 시작은 지금부터 ${APPROVE_MIN_LEAD}분 이후여야 합니다.`;
  else if (d.eventStart && toMs(d.hostingStart) > toMs(d.eventStart) - lead * MINUTE) e.hostingPeriod = `호스팅 시작은 이벤트 시작 ${lead}분 전까지여야 합니다.`;
  else if (d.eventEnd && d.hostingEnd < d.eventEnd) e.hostingPeriod = '호스팅 기간은 이벤트 기간을 모두 포함해야 합니다.';
  if (!d.banner) e.banner = '이벤트 대표 배너 이미지를 업로드해주세요.';
  if (d.type === 'COUPON') {
    if (d.coupons.some((c) => !c.name.trim() || !c.description.trim())) e.coupons = '쿠폰 이름과 Description을 모두 입력해주세요.';
    else if (d.coupons.some((c) => c.name.trim().length > 100 || c.description.trim().length > 500)) e.coupons = '쿠폰 이름은 100자, Description은 500자 이하로 입력해주세요.';
  }
  if (!parseStock(d.stock)) e.stock = `총 발급 재고를 1 ~ ${MAX_STOCK.toLocaleString('ko-KR')} 사이로 입력해주세요.`;
  if (!d.plan) e.plan = '트래픽 수용 인원 플랜을 선택해주세요.';
  if (!isHttpUrl(d.endpointUrl)) e.endpointUrl = '올바른 URL 형식이 아닙니다. (예: https://shop.example.com/tetra-entry)';
  else if (d.verifiedUrl !== d.endpointUrl) e.endpointUrl = '[확인] 버튼으로 URL 형식을 확인해주세요.';
  return e;
}

/** 신청 API 의 application 파트 */
export function toApplication(d: ApplyDraft): ApplyApplication {
  const eventType = d.type ?? 'COUPON';
  return {
    name: d.name.trim(),
    eventType,
    startAt: toApiDateTime(d.eventStart),
    endAt: toApiDateTime(d.eventEnd),
    hostingStartAt: toApiDateTime(d.hostingStart),
    hostingEndAt: toApiDateTime(d.hostingEnd),
    trafficPlanCode: d.plan ?? '',
    stockCount: parseStock(d.stock),
    endpointUrl: d.endpointUrl.trim(),
    coupons: eventType === 'COUPON' ? d.coupons.map((c) => ({ name: c.name.trim(), description: c.description.trim() })) : [],
    layout: d.screen,
  };
}

/** 서버 입력 오류(fields) 키 → 화면 항목 이름. coupons[0].name 같은 키는 앞부분으로 묶는다 */
const serverFieldLabel: Record<string, string> = {
  name: '이벤트 이름',
  eventType: '이벤트 타입',
  startAt: '이벤트 기간',
  endAt: '이벤트 기간',
  hostingStartAt: '도메인 호스팅 기간',
  hostingEndAt: '도메인 호스팅 기간',
  banner: '배너 이미지',
  coupons: '발급할 쿠폰',
  stockCount: '총 발급 재고',
  trafficPlanCode: '트래픽 수용 인원',
  endpointUrl: '이벤트 엔드포인트 URL',
  layout: '이벤트 화면 설정',
};

const infoErrorLabel: Record<InfoErrorKey, string> = {
  name: '이벤트 이름',
  eventPeriod: '이벤트 기간',
  hostingPeriod: '도메인 호스팅 기간',
  banner: '배너 이미지',
  coupons: '발급할 쿠폰',
  stock: '총 발급 재고',
  plan: '트래픽 수용 인원',
  endpointUrl: '이벤트 엔드포인트 URL',
};

/** 제출 직전 재검사 오류 — 서버 오류와 같은 모양('항목: 사유')으로 */
export function describeInfoErrors(errors: Partial<Record<InfoErrorKey, string>>) {
  return Object.entries(errors).map(([key, message]) => `${infoErrorLabel[key as InfoErrorKey]}: ${message}`);
}

export function describeServerErrors(fields: Record<string, string>) {
  return Object.entries(fields).map(([key, message]) => {
    const root = key.split(/[.[]/)[0];
    return `${serverFieldLabel[root] ?? key}: ${message}`;
  });
}
