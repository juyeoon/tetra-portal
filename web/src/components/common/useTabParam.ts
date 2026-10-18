import { useSearchParams } from 'react-router-dom';

/** 상세 화면 탭을 ?tab= 쿼리로 유지 (새로고침/뒤로가기 대응) */
export function useTabParam<T extends string>(tabs: readonly T[], fallback: T) {
  const [params, setParams] = useSearchParams();
  const raw = params.get('tab') as T | null;
  const tab = raw && tabs.includes(raw) ? raw : fallback;
  const setTab = (next: T) =>
    setParams(
      (prev) => {
        const p = new URLSearchParams(prev);
        p.set('tab', next);
        return p;
      },
      { replace: true },
    );
  return [tab, setTab] as const;
}
