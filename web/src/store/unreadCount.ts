/**
 * 사이드바 안 읽은 알림 배지 — 글자 · 읽기 이름 · 갱신 판단(화면과 분리, node --test 대상).
 * 값은 UnreadCountContext 하나가 들고, 알림센터 목록 응답의 unreadCount로도 맞춘다.
 */

/** 화면이 보이는 동안 다시 받는 간격 */
export const UNREAD_POLL_MS = 60_000;

/** 알림센터 — 목록 응답이 안 읽은 수를 함께 주므로 라우트 이동 때 따로 받지 않는다 */
export const NOTIFICATIONS_PATH = '/notifications';

/** 로그인 전 화면 */
const PUBLIC_PATHS = ['/login', '/password/setup'];

/** 배지 글자 — 0 · 모름이면 없음(null), 100 이상이면 99+ */
export function badgeText(count: number | null): string | null {
  if (count === null || count <= 0) return null;
  return count >= 100 ? '99+' : String(count);
}

/** 알림 센터 메뉴 링크의 읽기 이름 — 배지는 aria-hidden이라 수는 여기에만(99+가 아닌 실제 수) */
export function navAriaLabel(count: number | null): string {
  return badgeText(count) ? `알림 센터, 안 읽은 알림 ${count}건` : '알림 센터';
}

/** 배지에 마우스를 올렸을 때 */
export function badgeTitle(count: number): string {
  return `안 읽은 알림 ${count}건`;
}

/** 주기 · 다시 보일 때 받을지 — 로그인 상태이고 화면이 보일 때만(숨겨진 탭은 건너뛴다) */
export function shouldPoll({ authenticated, hidden }: { authenticated: boolean; hidden: boolean }): boolean {
  return authenticated && !hidden;
}

/** 라우트 이동 때 받을지 — 알림센터는 목록 응답으로 맞추고, 로그인 전 화면은 받지 않는다 */
export function fetchOnRoute(pathname: string): boolean {
  return pathname !== NOTIFICATIONS_PATH && !PUBLIC_PATHS.includes(pathname);
}

export interface UnreadState {
  /** 모르면(로그아웃 · 아직 못 받음) null */
  count: number | null;
  /** 마지막으로 반영한 요청 번호 — 이보다 앞선 요청의 늦은 응답은 버린다 */
  applied: number;
}

export const NO_UNREAD: UnreadState = { count: null, applied: 0 };

/** 응답 반영 — 앞선 요청의 늦은 응답은 버리고, 실패(null)면 이전 값 그대로 */
export function applyUnread(state: UnreadState, requestId: number, count: number | null): UnreadState {
  if (count === null || requestId < state.applied) return state;
  return { count, applied: requestId };
}

/** 로그아웃 — 값을 지우고, 그 전에 보낸 요청(번호 < nextRequestId)의 응답은 모두 버린다 */
export function clearUnread(nextRequestId: number): UnreadState {
  return { count: null, applied: nextRequestId };
}

/* ---------- 쓰기 요청 뒤 다시 받기 (공통 API 클라이언트가 응답마다 알린다) ---------- */

/** 쓰기 뒤 다시 받기까지 — 그 사이 끝난 쓰기는 한 번으로 묶는다 */
export const WRITE_REFRESH_MS = 300;

/** 이벤트 신청 뒤 한 번 더 — 신청 접수 · 실패 알림은 응답(202) 뒤 배너 업로드가 끝나야 생긴다 */
export const APPLY_FOLLOW_UP_MS = 3_000;

const WRITE_METHODS = ['POST', 'PUT', 'PATCH', 'DELETE'];

/** 쓰기여도 다시 받지 않는 요청 — 읽음 · 모두 읽음(따로 맞춘다) · 배지 자신 · 로그인 · 로그아웃 */
const NO_REFRESH_AFTER = [/^\/notifications\/[^/]+\/read$/, /^\/notifications\/read-all$/, /^\/notifications\/unread-count$/, /^\/auth\/(login|logout)$/];

const pathOnly = (path: string) => path.split('?')[0];

/** 요청이 끝난 뒤(성공 · 실패) 배지를 다시 받을지 — 쓰기(POST · PUT · PATCH · DELETE)만, 위 요청은 빼고 */
export function refreshAfterWrite(method: string, path: string): boolean {
  return WRITE_METHODS.includes(method.toUpperCase()) && !NO_REFRESH_AFTER.some((p) => p.test(pathOnly(path)));
}

/** 응답 뒤에 서버가 알림을 만드는 쓰기 — 이벤트 신청(POST /events) */
export function followUpAfterWrite(method: string, path: string): boolean {
  return method.toUpperCase() === 'POST' && pathOnly(path) === '/events';
}

export interface Timers {
  set: (run: () => void, ms: number) => unknown;
  clear: (id: unknown) => void;
}

const browserTimers: Timers = {
  set: (run, ms) => setTimeout(run, ms),
  clear: (id) => clearTimeout(id as ReturnType<typeof setTimeout>),
};

/** 몰린 요청을 묶는다 — 첫 request 뒤 delayMs에 run 한 번(그 사이 request는 같이 묶인다) */
export function createBatch(run: () => void, delayMs: number, timers: Timers = browserTimers) {
  let pending: unknown = null;
  return {
    request() {
      if (pending !== null) return;
      pending = timers.set(() => {
        pending = null;
        run();
      }, delayMs);
    },
    cancel() {
      if (pending !== null) timers.clear(pending);
      pending = null;
    },
  };
}

/**
 * 응답마다 부른다 — 쓰기면 WRITE_REFRESH_MS로 묶어 refresh, 이벤트 신청이면 APPLY_FOLLOW_UP_MS 뒤 한 번 더.
 * 로그아웃 상태면(isLive false) 아무것도 하지 않는다(refresh도 로그인 상태를 다시 본다)
 */
export function createWriteRefresher(refresh: () => void, isLive: () => boolean, timers: Timers = browserTimers) {
  const soon = createBatch(refresh, WRITE_REFRESH_MS, timers);
  const later = createBatch(refresh, APPLY_FOLLOW_UP_MS, timers);
  return {
    onResponse(method: string, path: string) {
      if (!isLive()) return;
      if (refreshAfterWrite(method, path)) soon.request();
      if (followUpAfterWrite(method, path)) later.request();
    },
    cancel() {
      soon.cancel();
      later.cancel();
    },
  };
}
