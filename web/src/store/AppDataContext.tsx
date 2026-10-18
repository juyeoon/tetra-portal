import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { mockAccount } from '@/mocks/account';
import { mockEvents } from '@/mocks/events';
import { mockNotifications, mockReports } from '@/mocks/notifications';
import { mockSettings } from '@/mocks/settings';
import type { Account, PortalEvent, PortalNotification, PortalSettings, Report, SlackSetting } from '@/types';
import { nowText } from '@/utils/format';

/** 이벤트 신청서 (Tab1~3 입력값) */
export type EventDraft = Pick<
  PortalEvent,
  'name' | 'type' | 'eventStart' | 'eventEnd' | 'hostingStart' | 'hostingEnd' | 'banner' | 'plan' | 'extraTraffic' | 'endpointUrl' | 'coupons' | 'screen'
>;

/**
 * 화면 구현용 임시 데이터 저장소.
 * API가 확정되면 각 액션을 API 호출로 교체하면 된다. (API 미연동 — TODO 표시)
 */
interface AppDataValue {
  account: Account;
  events: PortalEvent[];
  getEvent: (id: string) => PortalEvent | undefined;
  notifications: PortalNotification[];
  reports: Report[];
  settings: PortalSettings;

  /** 로그인 — 실패 사유는 '계정 정보 없음' 1종 (UI-TP-01) */
  login: (email: string, password: string) => Promise<boolean>;
  /** 고객센터 비밀번호 문의 메일 전송 (UI-TP-03) */
  sendSupportMail: (input: { from: string; subject: string; body: string }) => Promise<void>;
  /** 비밀번호 설정 (UI-TP-04) — false면 링크 만료 */
  setupPassword: (token: string, password: string) => Promise<boolean>;
  /** 이벤트 신청 (UI-TP-10) */
  submitEvent: (draft: EventDraft) => Promise<PortalEvent>;
  markNotificationRead: (id: number) => void;
  /** 리포트 이메일 전송 — 로그인 계정 이메일로 (UI-TP-12) */
  sendReportEmail: (id: number) => Promise<void>;
  setEmailEnabled: (enabled: boolean) => void;
  disconnectSlack: () => Promise<void>;
  connectSlack: () => Promise<void>;
  saveSlack: (patch: Pick<SlackSetting, 'workspace' | 'channels'>) => Promise<void>;
}

const AppDataContext = createContext<AppDataValue | null>(null);

const delay = (ms: number) => new Promise((r) => setTimeout(r, ms));

export function AppDataProvider({ children }: { children: ReactNode }) {
  const [events, setEvents] = useState<PortalEvent[]>(mockEvents);
  const [notifications, setNotifications] = useState<PortalNotification[]>(mockNotifications);
  const [settings, setSettings] = useState<PortalSettings>(mockSettings);

  const getEvent = useCallback((id: string) => events.find((e) => e.id === id), [events]);

  // TODO: 로그인 API 연동. Mock: mockAccount 이메일 + 아무 비밀번호면 성공
  const login = useCallback(async (email: string, password: string) => {
    await delay(400);
    return email.trim().toLowerCase() === mockAccount.email && password.length > 0;
  }, []);

  // TODO: 고객센터 메일 전송 API 연동
  const sendSupportMail = useCallback(async () => {
    await delay(500);
  }, []);

  // TODO: 비밀번호 설정 API 연동. Mock: token이 'expired'면 만료
  const setupPassword = useCallback(async (token: string) => {
    await delay(500);
    return token !== 'expired';
  }, []);

  // TODO: 이벤트 신청 API 연동. 신청 직후 상태는 신청됨(처리 중)
  const submitEvent = useCallback(async (draft: EventDraft) => {
    await delay(600);
    const at = nowText();
    const no = Math.max(...events.map((e) => Number(e.id.split('-evt-')[1]?.split('-')[0]) || 0)) + 1;
    const created: PortalEvent = {
      ...draft,
      id: `${mockAccount.tenantId}-evt-${no}-01`,
      appliedAt: at,
      applicant: `${mockAccount.managerName} (테넌트 관리자)`,
      applicationStatus: 'APPLYING',
      logs: [{ status: 'APPLYING', at, by: `${mockAccount.managerName} (테넌트 관리자)` }],
    };
    setEvents((prev) => [created, ...prev]);
    return created;
  }, [events]);

  // TODO: 알림 읽음 처리 API 연동
  const markNotificationRead = useCallback((id: number) => {
    setNotifications((prev) => prev.map((n) => (n.id === id ? { ...n, read: true } : n)));
  }, []);

  // TODO: 리포트 이메일 전송 API 연동
  const sendReportEmail = useCallback(async () => {
    await delay(500);
  }, []);

  // TODO: 이메일 송수신 설정 API 연동
  const setEmailEnabled = useCallback((enabled: boolean) => {
    setSettings((prev) => ({ ...prev, emailEnabled: enabled }));
  }, []);

  // TODO: Slack 연동 해제 API 연동
  const disconnectSlack = useCallback(async () => {
    await delay(400);
    setSettings((prev) => ({ ...prev, slack: { ...prev.slack, connected: false, connectedLabel: undefined } }));
  }, []);

  // TODO: Slack OAuth 연동 (새 창에서 워크스페이스 승인 후 돌아옴)
  const connectSlack = useCallback(async () => {
    await delay(600);
    setSettings((prev) => ({
      ...prev,
      slack: { ...prev.slack, connected: true, connectedLabel: `${prev.slack.workspace || 'TenantCompany'} Workspace (${mockAccount.email})` },
    }));
  }, []);

  // TODO: 워크스페이스·채널 저장 API 연동
  const saveSlack = useCallback(async (patch: Pick<SlackSetting, 'workspace' | 'channels'>) => {
    await delay(400);
    setSettings((prev) => ({ ...prev, slack: { ...prev.slack, ...patch } }));
  }, []);

  const value = useMemo(
    () => ({
      account: mockAccount,
      events,
      getEvent,
      notifications,
      reports: mockReports,
      settings,
      login,
      sendSupportMail,
      setupPassword,
      submitEvent,
      markNotificationRead,
      sendReportEmail,
      setEmailEnabled,
      disconnectSlack,
      connectSlack,
      saveSlack,
    }),
    [events, getEvent, notifications, settings, login, sendSupportMail, setupPassword, submitEvent, markNotificationRead, sendReportEmail, setEmailEnabled, disconnectSlack, connectSlack, saveSlack],
  );

  return <AppDataContext.Provider value={value}>{children}</AppDataContext.Provider>;
}

export function useAppData() {
  const ctx = useContext(AppDataContext);
  if (!ctx) throw new Error('useAppData must be used within AppDataProvider');
  return ctx;
}
