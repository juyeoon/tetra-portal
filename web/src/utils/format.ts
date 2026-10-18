export const formatNumber = (n: number) => n.toLocaleString('ko-KR');

export const formatWon = (n: number) => `${formatNumber(n)}원`;

export const formatPercent = (part: number, total: number, digits = 1) =>
  total === 0 ? '0%' : `${((part / total) * 100).toFixed(digits)}%`;

/** '2026.10.03 00:00' ~ '2026.10.14 23:59' → '2026.10.03 00:00 ~ 10.14 23:59' (같은 해면 연도 생략) */
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

/** 현재 시각 'YYYY.MM.DD HH:mm' */
export function nowText() {
  const pad = (n: number) => String(n).padStart(2, '0');
  const d = new Date();
  return `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** 브라우저에서 텍스트 파일 내려받기 */
export function downloadText(filename: string, content: string, mime = 'text/csv;charset=utf-8') {
  // 엑셀에서 한글이 깨지지 않도록 BOM 추가
  const blob = new Blob(['\uFEFF', content], { type: mime });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}
