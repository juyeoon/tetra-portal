import type { Account } from '@/types';

export const mockAccount: Account = {
  companyName: '테넌트컴퍼니',
  tenantId: 'tnt-3f8a21',
  managerName: '다란',
  email: 'admin@tenant-company.com',
};

/** 고객센터(UI-TP-03) 수신 주소 */
export const SUPPORT_EMAIL = 'tetraops@gmail.com';

/** 로그인 화면 좌측 하단 버전 표기 */
export const APP_VERSION = 'v1.0.0';
