import { Typography } from '@mui/material';
import AssessmentOutlinedIcon from '@mui/icons-material/AssessmentOutlined';
import { errorMessage } from '@/api/client';
import { getReportDownload, listReports, sendReportEmail } from '@/api/portal';
import { useApi } from '@/api/useApi';
import { ErrorView, LoadingView } from '@/components/common/LoadState';
import SectionTitle from '@/components/common/SectionTitle';
import StateMessage from '@/components/common/StateMessage';
import PageHeader from '@/components/layout/PageHeader';
import ReportTable from '@/components/report/ReportTable';
import { useAccount } from '@/store/AuthContext';
import { useToast } from '@/store/ToastContext';
import { text } from '@/theme/theme';
import type { Report } from '@/types';
import { openDownload } from '@/utils/format';

/** UI-TP-12 리포트 — 감사 로그 다운로드 · 이메일 전송. 리포트는 매월 자동 생성 */
export default function ReportPage() {
  const toast = useToast();
  const account = useAccount();
  const { data: reports, error, reload } = useApi(listReports, []);

  // 5분짜리 다운로드 링크를 받아 바로 연다
  const download = async (r: Report) => {
    try {
      openDownload((await getReportDownload(r.reportId)).url);
    } catch (e) {
      toast(errorMessage(e), 'error');
    }
  };

  // 전송 대상은 로그인 계정 이메일 (서버가 정한다)
  const send = async (r: Report) => {
    try {
      await sendReportEmail(r.reportId);
      toast(`${account.email}로 "${r.name}"을(를) 전송했습니다.`);
    } catch (e) {
      toast(errorMessage(e), 'error');
    }
  };

  return (
    <>
      <PageHeader eyebrow="REPORTS" breadcrumbs={['리포트']} />
      <SectionTitle>최근 생성된 리포트</SectionTitle>
      {error ? (
        <ErrorView message={error.message} onRetry={reload} />
      ) : !reports ? (
        <LoadingView />
      ) : reports.length === 0 ? (
        <StateMessage icon={<AssessmentOutlinedIcon />} tone="grey" title="생성된 리포트가 없습니다" description="리포트는 매월 자동으로 생성됩니다." />
      ) : (
        <>
          <ReportTable reports={reports} onDownload={download} onSendEmail={send} />
          <Typography variant="body2" sx={{ mt: 2, color: text.tertiary }}>
            ※ 리포트는 매월 자동으로 생성되며, 이메일은 로그인 계정({account.email})으로 전송됩니다.
          </Typography>
        </>
      )}
    </>
  );
}
