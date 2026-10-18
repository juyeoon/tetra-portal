import type { PortalSettings } from '@/types';

/** 설정 (UI-TP-14) — Slack 연동 완료 상태 */
export const mockSettings: PortalSettings = {
  emailEnabled: true,
  slack: {
    connected: true,
    connectedLabel: 'TenantCompany Workspace (admin@tenant-company.com)',
    workspace: 'TenantCompany',
    channels: {
      CRITICAL: '#tenant-company-critical',
      WARNING: '#tenant-company-warning',
      INFO: '#tenant-company-info',
    },
  },
};
