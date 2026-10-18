import { createContext, useContext } from 'react';

interface LayoutValue {
  openNav: () => void;
  /** 상단바 영역 — PageHeader가 portal로 렌더링한다 */
  topbar: HTMLElement | null;
}

export const LayoutContext = createContext<LayoutValue>({ openNav: () => {}, topbar: null });

export const useLayout = () => useContext(LayoutContext);
