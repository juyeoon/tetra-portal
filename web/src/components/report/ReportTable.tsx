import { useState } from 'react';
import { Button, TableBody, TableCell, TableHead, TableRow } from '@mui/material';
import FileDownloadOutlinedIcon from '@mui/icons-material/FileDownloadOutlined';
import MailOutlineRoundedIcon from '@mui/icons-material/MailOutlineRounded';
import TableCard from '@/components/common/TableCard';
import { size } from '@/theme/theme';
import type { Report } from '@/types';

interface Props {
  reports: Report[];
  onDownload: (report: Report) => void;
  onSendEmail: (report: Report) => Promise<void>;
}

/**
 * "최근 생성된 리포트" 표 — 리포트명 / 기간 / 생성일 / 다운로드 / 이메일 전송 (UI-TP-12).
 * 콘솔 리포트 화면에서도 쓸 수 있도록 데이터·동작은 props로만 받는다.
 */
export default function ReportTable({ reports, onDownload, onSendEmail }: Props) {
  const [sendingId, setSendingId] = useState<number | null>(null);

  const send = async (r: Report) => {
    setSendingId(r.id);
    await onSendEmail(r);
    setSendingId(null);
  };

  return (
    <TableCard minWidth={720}>
      <TableHead>
        <TableRow>
          <TableCell>리포트명</TableCell>
          <TableCell>기간</TableCell>
          <TableCell>생성일</TableCell>
          <TableCell align="center" width={140}>
            다운로드
          </TableCell>
          <TableCell align="center" width={160}>
            이메일 전송
          </TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {reports.map((r) => (
          <TableRow key={r.id}>
            <TableCell>{r.name}</TableCell>
            <TableCell sx={{ whiteSpace: 'nowrap' }}>{r.period}</TableCell>
            <TableCell sx={{ whiteSpace: 'nowrap' }}>{r.createdAt}</TableCell>
            <TableCell align="center">
              <Button size="small" variant="outlined" startIcon={<FileDownloadOutlinedIcon sx={{ fontSize: `${size.iconSm} !important` }} />} onClick={() => onDownload(r)}>
                다운로드
              </Button>
            </TableCell>
            <TableCell align="center">
              <Button
                size="small"
                variant="contained"
                startIcon={<MailOutlineRoundedIcon sx={{ fontSize: `${size.iconSm} !important` }} />}
                onClick={() => send(r)}
                disabled={sendingId !== null}
              >
                {sendingId === r.id ? '전송 중…' : '이메일 전송'}
              </Button>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </TableCard>
  );
}
