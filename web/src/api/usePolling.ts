import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from './client';

interface PollingOptions<T> {
  intervalMs: number;
  /** false면 부르지 않는다(처음 한 번도) */
  enabled?: boolean;
  /** 이 응답 뒤로는 주기 호출을 멈춘다(예: 종료된 이벤트) */
  stopWhen?: (data: T) => boolean;
}

interface PollingState<T> {
  data: T | undefined;
  /** 마지막 호출의 오류. 다음 호출이 성공하면 지운다 — data는 마지막 성공 값 그대로 */
  error: ApiError | null;
  /** 마지막 성공 응답을 받은 시각(performance.now, 화면 시계가 아닌 흐른 시간 계산용) */
  receivedAt: number | null;
  /** 지금 한 번 더 부른다 */
  refresh: () => void;
}

/**
 * 주기 호출. 처음 한 번 부르고 intervalMs마다 다시 부른다. 탭이 숨겨지면 건너뛰고, 다시 보이면 바로 부른다.
 * deps가 바뀌면 값을 비우고 처음부터 — 늦게 온 이전 응답(앞선 요청 포함)은 버린다. 401은 client가 로그인 화면으로 보낸다.
 */
export function usePolling<T>(load: () => Promise<T>, deps: unknown[], { intervalMs, enabled = true, stopWhen }: PollingOptions<T>): PollingState<T> {
  const [data, setData] = useState<T>();
  const [error, setError] = useState<ApiError | null>(null);
  const [receivedAt, setReceivedAt] = useState<number | null>(null);
  const loadRef = useRef(load);
  loadRef.current = load;
  // deps 묶음 번호 · 요청 번호 — 다른 묶음이거나 이미 반영한 요청보다 앞선 응답은 버린다
  const generation = useRef(0);
  const requested = useRef(0);
  const applied = useRef(0);

  const run = useCallback(() => {
    const gen = generation.current;
    const id = ++requested.current;
    loadRef
      .current()
      .then((d) => {
        if (gen !== generation.current || id < applied.current) return;
        applied.current = id;
        setData(d);
        setError(null);
        setReceivedAt(performance.now());
      })
      .catch((e: unknown) => {
        if (gen !== generation.current || id < applied.current) return;
        setError(e instanceof ApiError ? e : new ApiError(0, 'INTERNAL_ERROR', '처리 중 오류가 발생했습니다.'));
      });
  }, []);

  useEffect(() => {
    generation.current += 1;
    applied.current = requested.current;
    setData(undefined);
    setError(null);
    setReceivedAt(null);
    if (enabled) run();
    // 부르는 조건은 deps로 받는다(load는 매번 새 함수라 넣지 않는다)
  }, [...deps, enabled]);

  const stopped = data !== undefined && stopWhen?.(data) === true;

  useEffect(() => {
    if (!enabled || stopped) return;
    const timer = window.setInterval(() => {
      if (!document.hidden) run();
    }, intervalMs);
    const onVisible = () => {
      if (!document.hidden) run();
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [...deps, enabled, stopped, intervalMs]);

  return { data, error, receivedAt, refresh: run };
}
