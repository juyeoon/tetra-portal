export const formatNumber = (n: number) => n.toLocaleString('ko-KR');

export const formatPercent = (part: number, total: number, digits = 1) =>
  total === 0 ? '0%' : `${((part / total) * 100).toFixed(digits)}%`;

/**
 * API 일시(ISO +09:00, 예: '2026-11-27T20:00:00+09:00') → 'YYYY.MM.DD HH:mm'.
 * 서버가 항상 한국 시각(+09:00)으로 보내므로 브라우저 시간대와 상관없이 글자를 그대로 잘라 쓴다.
 */
export function formatDateTime(iso: string | null | undefined) {
  if (!iso) return '-';
  return `${iso.slice(0, 10).replaceAll('-', '.')} ${iso.slice(11, 16)}`;
}

/** API 일시 → 'YYYY.MM.DD' */
export const formatDate = (iso: string | null | undefined) => (iso ? iso.slice(0, 10).replaceAll('-', '.') : '-');

/** API 일시 → 'MM.DD HH:mm' */
export const formatMonthDayTime = (iso: string) => `${iso.slice(5, 10).replace('-', '.')} ${iso.slice(11, 16)}`;

/** API 일시 → 'HH:mm' */
export const formatTime = (iso: string) => iso.slice(11, 16);

/** 0~1 비율 → '12.3%' */
export const formatRate = (rate: number | null | undefined, digits = 1) => (rate == null ? '-' : `${(rate * 100).toFixed(digits)}%`);

/** 표시 형식('YYYY.MM.DD HH:mm') 두 개 — '2026.10.03 00:00' ~ '2026.10.14 23:59' → '2026.10.03 00:00 ~ 10.14 23:59' (같은 해면 연도 생략) */
export function formatPeriod(start: string, end: string) {
  const sameYear = start.slice(0, 4) === end.slice(0, 4);
  return `${start} ~ ${sameYear ? end.slice(5) : end}`;
}

/** 날짜만 — '2026.09.20 00:00' ~ '2026.09.27 23:59' → '2026.09.20 ~ 09.27' */
export function formatDateRange(start: string, end: string) {
  return formatPeriod(start.slice(0, 10), end.slice(0, 10));
}

export async function copyText(text: string) {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}

/** 내려받기 링크(presigned URL) 열기 — 응답이 첨부 파일이라 현재 화면은 그대로 남는다 */
export function openDownload(url: string) {
  const a = document.createElement('a');
  a.href = url;
  a.rel = 'noopener';
  a.click();
}
