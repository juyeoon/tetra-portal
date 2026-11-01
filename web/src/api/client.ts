/**
 * Portal API 호출 — 같은 출처의 상대 경로 `/api/portal` (화면과 API가 한 이미지, 로컬은 Vite proxy).
 * 인증은 HttpOnly 쿠키(TETRA_PORTAL_TOKEN)라 화면 코드가 토큰을 다루지 않는다.
 * 오류는 서버 형식 { code, message, fields? } 을 ApiError 로 던진다 — message 는 화면에 그대로 보여도 된다.
 */

const BASE = '/api/portal';

export class ApiError extends Error {
  readonly status: number;
  /** 서버 오류 코드 (예: LOGIN_FAILED, INVALID_INPUT). 네트워크 오류면 NETWORK_ERROR */
  readonly code: string;
  /** 400일 때 항목별 사유 */
  readonly fields: Record<string, string>;

  constructor(status: number, code: string, message: string, fields: Record<string, string> = {}) {
    super(message);
    this.status = status;
    this.code = code;
    this.fields = fields;
  }
}

/** 로그인이 풀렸을 때(401) — AuthContext 가 등록한다 */
let onUnauthorized: (() => void) | null = null;
export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler;
}

/** 응답을 받은 요청마다(성공 · 실패 모두, 네트워크 오류는 빼고) — UnreadCountContext가 등록한다(쓰기 뒤 배지 다시 받기) */
let onResponse: ((method: string, path: string) => void) | null = null;
export function setResponseListener(listener: ((method: string, path: string) => void) | null) {
  onResponse = listener;
}

interface Options {
  /** JSON 본문 */
  json?: unknown;
  /** multipart 본문 (Content-Type 은 브라우저가 정한다) */
  form?: FormData;
  /** false면 401이어도 로그인 화면으로 보내지 않는다 (로그인 · 비밀번호 설정 · 계정 확인) */
  redirectOn401?: boolean;
}

export async function api<T>(method: string, path: string, { json, form, redirectOn401 = true }: Options = {}): Promise<T> {
  let response: Response;
  try {
    response = await fetch(BASE + path, {
      method,
      credentials: 'same-origin',
      headers: json !== undefined ? { 'Content-Type': 'application/json', Accept: 'application/json' } : { Accept: 'application/json' },
      body: json !== undefined ? JSON.stringify(json) : form,
    });
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', '서버에 연결하지 못했습니다. 잠시 뒤 다시 시도해 주세요.');
  }
  onResponse?.(method, path);

  if (response.status === 204) return undefined as T;

  const body = await response.json().catch(() => null);
  if (response.ok) return body as T;

  const code = typeof body?.code === 'string' ? body.code : 'INTERNAL_ERROR';
  const message = typeof body?.message === 'string' ? body.message : '처리 중 오류가 발생했습니다.';
  if (response.status === 401 && redirectOn401) onUnauthorized?.();
  throw new ApiError(response.status, code, message, body?.fields ?? {});
}

/** 화면에 보여 줄 오류 문구 */
export function errorMessage(e: unknown) {
  return e instanceof ApiError ? e.message : '처리 중 오류가 발생했습니다.';
}
