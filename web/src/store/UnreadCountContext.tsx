import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { setResponseListener } from '@/api/client';
import { getUnreadNotificationCount } from '@/api/portal';
import { useAuth } from './AuthContext';
import { NO_UNREAD, UNREAD_POLL_MS, applyUnread, clearUnread, createWriteRefresher, fetchOnRoute, shouldPoll } from './unreadCount';

interface UnreadValue {
  /** 안 읽은 알림 수 — 모르면(로그아웃 · 아직 못 받음) null */
  count: number | null;
  /** 지금 다시 받는다(읽음 처리 직후 등) */
  refresh: () => void;
  /** 이미 받은 값으로 맞춘다(알림센터 목록 응답의 unreadCount) */
  report: (count: number) => void;
}

const UnreadCountContext = createContext<UnreadValue | null>(null);

/**
 * 사이드바 안 읽은 알림 배지 값 — 앱 전체에 하나.
 * 받는 때: 로그인 상태가 될 때(앱 열림 · 로그인 직후) · 라우트 이동 · 보이는 동안 60초마다(숨겨지면 멈추고 다시 보이면 바로) ·
 * 쓰기 요청이 끝난 뒤(300ms로 묶음, 이벤트 신청은 3초 뒤 한 번 더) · refresh.
 * 실패하면 마지막 값 그대로(토스트 · 콘솔 없음). 로그아웃하면 멈추고 지운다. 401은 client가 로그아웃 상태로 바꾼다.
 */
export function UnreadCountProvider({ children }: { children: ReactNode }) {
  const { status, account } = useAuth();
  const authenticated = status === 'authenticated';
  const { pathname } = useLocation();
  const [state, setState] = useState(NO_UNREAD);
  const requested = useRef(0);
  const live = useRef(authenticated);
  live.current = authenticated;

  const refresh = useCallback(() => {
    if (!live.current) return;
    const id = ++requested.current;
    getUnreadNotificationCount().then(
      (r) => setState((prev) => applyUnread(prev, id, r.count)),
      () => setState((prev) => applyUnread(prev, id, null)),
    );
  }, []);

  const report = useCallback((count: number) => {
    if (!live.current) return;
    const id = ++requested.current;
    setState((prev) => applyUnread(prev, id, count));
  }, []);

  // 로그아웃 — 지우고, 보내 둔 요청의 응답도 버린다
  useEffect(() => {
    if (!authenticated) setState(clearUnread(++requested.current));
  }, [authenticated]);

  // 앱이 열릴 때 · 로그인 직후 · 라우트 이동 때
  useEffect(() => {
    if (authenticated && fetchOnRoute(pathname)) refresh();
  }, [authenticated, account?.tenantId, pathname, refresh]);

  // 쓰기 요청이 끝나면(성공 · 실패) 300ms로 묶어 다시 받는다 — 화면마다 붙이지 않고 공통 API 클라이언트가 알린다
  useEffect(() => {
    const writes = createWriteRefresher(refresh, () => live.current);
    setResponseListener(writes.onResponse);
    return () => {
      setResponseListener(null);
      writes.cancel();
    };
  }, [refresh]);

  // 보이는 동안 60초마다, 숨겨졌다 다시 보이면 바로
  useEffect(() => {
    if (!authenticated) return;
    const tick = () => {
      if (shouldPoll({ authenticated: live.current, hidden: document.hidden })) refresh();
    };
    const timer = window.setInterval(tick, UNREAD_POLL_MS);
    document.addEventListener('visibilitychange', tick);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener('visibilitychange', tick);
    };
  }, [authenticated, refresh]);

  const value = useMemo(() => ({ count: state.count, refresh, report }), [state.count, refresh, report]);
  return <UnreadCountContext.Provider value={value}>{children}</UnreadCountContext.Provider>;
}

export function useUnreadCount() {
  const ctx = useContext(UnreadCountContext);
  if (!ctx) throw new Error('useUnreadCount must be used within UnreadCountProvider');
  return ctx;
}
