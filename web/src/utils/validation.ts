export const isEmail = (v: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v.trim());

/** 엔드포인트 URL 형식 검증 — http(s)만 허용, 실제 접속 여부는 확인하지 않는다 (UI-TP-08) */
export function isHttpUrl(v: string) {
  try {
    const url = new URL(v.trim());
    return (url.protocol === 'https:' || url.protocol === 'http:') && url.hostname.includes('.');
  } catch {
    return false;
  }
}

/** Slack 채널명 — #으로 시작, 소문자·숫자·-·_ (최대 80자) */
export const isSlackChannel = (v: string) => /^#[a-z0-9][a-z0-9_-]{0,79}$/.test(v.trim());
