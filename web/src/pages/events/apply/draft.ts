import type { ApplyApplication } from '@/api/portal';
import type { EventType, ScreenSetting } from '@/types';
import { toApiDateTime } from '@/utils/event';
import { isHttpUrl } from '@/utils/validation';

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

/** 지금(브라우저 시각)을 datetime-local 형식으로 — 비교용 */
function nowLocal() {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export type InfoErrorKey = 'name' | 'eventPeriod' | 'hostingPeriod' | 'banner' | 'coupons' | 'stock' | 'plan' | 'endpointUrl';

/** 이벤트 정보 단계(Tab2) 입력 오류 — 키별 메시지. 비어 있으면 다음 단계로 진행 가능 (서버도 같은 규칙으로 다시 검사) */
export function infoErrors(d: ApplyDraft) {
  const e: Partial<Record<InfoErrorKey, string>> = {};
  if (!d.name.trim()) e.name = '이벤트 이름을 입력해주세요.';
  else if (d.name.trim().length > 100) e.name = '100자 이하로 입력해주세요.';
  if (!d.eventStart || !d.eventEnd) e.eventPeriod = '이벤트 시작·종료 일시를 입력해주세요.';
  else if (d.eventEnd <= d.eventStart) e.eventPeriod = '종료 일시는 시작 일시보다 뒤여야 합니다.';
  if (!d.hostingStart || !d.hostingEnd) e.hostingPeriod = '도메인 호스팅 시작·종료 일시를 입력해주세요.';
  else if (d.hostingEnd <= d.hostingStart) e.hostingPeriod = '종료 일시는 시작 일시보다 뒤여야 합니다.';
  else if (d.hostingStart <= nowLocal()) e.hostingPeriod = '호스팅 시작은 지금보다 뒤여야 합니다.';
  else if (d.eventStart && d.eventEnd && (d.hostingStart > d.eventStart || d.hostingEnd < d.eventEnd)) e.hostingPeriod = '호스팅 기간은 이벤트 기간을 모두 포함해야 합니다.';
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

export function describeServerErrors(fields: Record<string, string>) {
  return Object.entries(fields).map(([key, message]) => {
    const root = key.split(/[.[]/)[0];
    return `${serverFieldLabel[root] ?? key}: ${message}`;
  });
}
