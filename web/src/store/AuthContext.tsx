import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import * as authApi from '@/api/auth';
import { ApiError, setUnauthorizedHandler } from '@/api/client';
import type { Account } from '@/types';

type Status = 'loading' | 'authenticated' | 'anonymous';

interface AuthValue {
  status: Status;
  /** status 가 authenticated 일 때만 있다 */
  account: Account | null;
  /** 성공 true, 계정 정보 불일치(LOGIN_FAILED) false. 그 밖의 오류는 던진다 */
  login: (email: string, password: string) => Promise<boolean>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthValue | null>(null);

/** 로그인 상태. 시작할 때 쿠키가 살아 있는지 /me 로 확인하고, 어떤 API든 401이 오면 로그아웃 상태로 바꾼다. */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<Status>('loading');
  const [account, setAccount] = useState<Account | null>(null);

  const signOut = useCallback(() => {
    setAccount(null);
    setStatus('anonymous');
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(signOut);
    authApi
      .me()
      .then((a) => {
        setAccount(a);
        setStatus('authenticated');
      })
      .catch(signOut);
    return () => setUnauthorizedHandler(null);
  }, [signOut]);

  const login = useCallback(async (email: string, password: string) => {
    try {
      const a = await authApi.login(email.trim(), password);
      setAccount(a);
      setStatus('authenticated');
      return true;
    } catch (e) {
      if (e instanceof ApiError && (e.code === 'LOGIN_FAILED' || e.code === 'INVALID_INPUT')) return false;
      throw e;
    }
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      signOut();
    }
  }, [signOut]);

  const value = useMemo(() => ({ status, account, login, logout }), [status, account, login, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}

/** 로그인한 계정 — 포털 화면(RequireAuth 안)에서만 쓴다 */
export function useAccount(): Account {
  const { account } = useAuth();
  if (!account) throw new Error('useAccount must be used inside RequireAuth');
  return account;
}
