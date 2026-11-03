import type { PasswordLink } from '@/api/auth';
// node --test 대상 — 실행 시 불러오는 파일은 상대 경로 + .ts (Node가 @/ 를 풀지 못함)

/** t · exp · sig. 하나라도 없으면 null */
function fromParams(params: URLSearchParams): PasswordLink | null {
  const t = params.get('t');
  const exp = Number(params.get('exp'));
  const sig = params.get('sig');
  return t && sig && Number.isInteger(exp) && exp > 0 ? { t, exp, sig } : null;
}

/**
 * 메일 링크의 t · exp · sig — 프래그먼트(#t=&exp=&sig=)를 먼저, 없으면 쿼리(?t=&exp=&sig=).
 * 프래그먼트는 서버로 가지 않아 접근 로그에 남지 않는다. 한 곳에서 셋이 다 있어야 하고(섞지 않음), 없으면 null (만료 화면)
 * @param hash location.hash (`#…` 또는 빈 문자열)
 * @param search location.search (`?…` 또는 빈 문자열)
 */
export function readPasswordLink(hash: string, search: string): PasswordLink | null {
  return fromParams(new URLSearchParams(hash.startsWith('#') ? hash.slice(1) : hash)) ?? fromParams(new URLSearchParams(search));
}
