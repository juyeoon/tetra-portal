import { Typography } from '@mui/material';
import AssessmentOutlinedIcon from '@mui/icons-material/AssessmentOutlined';
import SectionTitle from '@/components/common/SectionTitle';
import StateMessage from '@/components/common/StateMessage';
import PageHeader from '@/components/layout/PageHeader';
import ReportTable from '@/components/report/ReportTable';
import { useAppData } from '@/store/AppDataContext';
import { useToast } from '@/store/ToastContext';
import { text } from '@/theme/theme';
import type { Report } from '@/types';
import { downloadText } from '@/utils/format';

/** UI-TP-12 리포트 — 감사 로그 다운로드 · 이메일 전송. 리포트는 매월 자동 생성 */
export default function ReportPage() {
  const toast = useToast();
  const { reports, account, sendReportEmail } = useAppData();

  // TODO: 감사 로그 파일 다운로드 API 연동 (현재는 빈 CSV)
  const download = (r: Report) => downloadText(`${r.name.replaceAll(' ', '_')}.csv`, `리포트명,기간,생성일\n${r.name},${r.period},${r.createdAt}\n`);

  // 전송 대상은 로그인 계정 이메일로 자동 입력
  const send = async (r: Report) => {
    await sendReportEmail(r.id);
    toast(`${account.email}로 "${r.name}"을(를) 전송했습니다.`);
  };

  return (
    <>
      <PageHeader eyebrow="REPORTS" breadcrumbs={['리포트']} />
      <SectionTitle>최근 생성된 리포트</SectionTitle>
      {reports.length === 0 ? (
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
