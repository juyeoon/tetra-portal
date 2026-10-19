import { useCallback, useEffect, useState } from 'react';
import { ApiError } from './client';

interface ApiState<T> {
  data: T | undefined;
  error: ApiError | null;
  loading: boolean;
  /** 다시 불러오기 (화면에 있던 data 는 그대로 두고 바꾼다) */
  reload: () => void;
  /** 응답을 받은 뒤 화면에서 직접 바꿀 때 (예: 읽음 표시) */
  setData: (update: (prev: T | undefined) => T | undefined) => void;
}

/**
 * 화면이 열릴 때 API 하나를 부른다. deps 가 바뀌면 다시 부르고, 늦게 온 이전 응답은 버린다.
 * 401은 client 가 로그인 화면으로 보낸다.
 */
export function useApi<T>(load: () => Promise<T>, deps: unknown[]): ApiState<T> {
  const [data, setDataState] = useState<T>();
  const [error, setError] = useState<ApiError | null>(null);
  const [loading, setLoading] = useState(true);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    load()
      .then((d) => {
        if (cancelled) return;
        setDataState(d);
        setError(null);
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        setError(e instanceof ApiError ? e : new ApiError(0, 'INTERNAL_ERROR', '처리 중 오류가 발생했습니다.'));
      })
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
    // 부르는 조건은 deps 로 받는다 (load 는 매번 새 함수라 넣지 않는다)
  }, [...deps, tick]);

  const reload = useCallback(() => setTick((t) => t + 1), []);
  const setData = useCallback((update: (prev: T | undefined) => T | undefined) => setDataState(update), []);

  return { data, error, loading, reload, setData };
}
