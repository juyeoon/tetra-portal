import type { Account } from '@/types';
import { api } from './client';

/** UI-TP-01 로그인 — 성공하면 쿠키가 내려온다. 실패는 사유와 상관없이 401 LOGIN_FAILED */
export const login = (email: string, password: string) =>
  api<Account>('POST', '/auth/login', { json: { email, password }, redirectOn401: false });

export const logout = () => api<void>('POST', '/auth/logout', { redirectOn401: false });

/** 로그인한 계정 — 로그인 전이면 401 */
export const me = () => api<Account>('GET', '/me', { redirectOn401: false });

/** UI-TP-04 메일 링크(?t=&exp=&sig=) 확인 → 이메일. 만료 · 사용됨 · 위조는 410 PASSWORD_LINK_INVALID */
export interface PasswordLink {
  t: string;
  exp: number;
  sig: string;
}

export const verifyPasswordLink = (link: PasswordLink) =>
  api<{ email: string }>('POST', '/password/setup/verify', { json: link, redirectOn401: false });

export const setupPassword = (link: PasswordLink, password: string) =>
  api<void>('POST', '/password/setup', { json: { ...link, password }, redirectOn401: false });

/** UI-TP-03 문의 메일 (로그인 전). 메일 발송 실패는 503 MAIL_SEND_FAILED */
export const sendSupportMail = (input: { from: string; subject: string; body: string }) =>
  api<void>('POST', '/support-mail', { json: input, redirectOn401: false });
