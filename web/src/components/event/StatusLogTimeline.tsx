import { Box, Stack, Typography } from '@mui/material';
import StatusChip from '@/components/common/StatusChip';
import { line, radius, text, tones } from '@/theme/theme';
import type { StatusLog } from '@/types';
import { formatDateTime } from '@/utils/format';
import { actorLabel, statusMetaOf, statusTypeLabel } from '@/utils/status';

/**
 * 상태 처리 기록 (Status Log) — 신청 · 호스팅 · 이벤트 상태가 바뀐 시각과 처리 주체, 반려 사유 · 실패 원인.
 * 콘솔 OPS-03-2·04-2 "신청 내용"에서도 쓸 수 있도록 상세 응답 전체에 의존하지 않는다.
 */
export default function StatusLogTimeline({ logs, applicantName }: { logs: StatusLog[]; applicantName: string }) {
  if (logs.length === 0) {
    return (
      <Typography variant="body2" sx={{ color: text.tertiary }}>
        기록이 없습니다.
      </Typography>
    );
  }
  return (
    <Box component="ol" sx={{ listStyle: 'none', m: 0, p: 0 }}>
      {logs.map((log, i) => {
        const meta = statusMetaOf(log.statusType, log.newStatus);
        const last = i === logs.length - 1;
        const rejected = log.statusType === 'APPLICATION' && log.newStatus === 'REJECTED';
        const box = rejected ? tones.red : tones.grey;
        return (
          <Box component="li" key={`${log.statusType}-${log.newStatus}-${log.changedAt}`} sx={{ position: 'relative', pl: 3, pb: last ? 0 : 2.5 }}>
            {/* 점 + 세로선 */}
            <Box sx={{ position: 'absolute', left: 3, top: 9, width: 9, height: 9, bgcolor: tones[meta.tone].dot }} />
            {!last && <Box sx={{ position: 'absolute', left: 7, top: 22, bottom: 2, width: '1px', bgcolor: line.base }} />}

            <Stack direction="row" spacing={0.75} alignItems="center">
              <Typography variant="body2" sx={{ color: text.tertiary }}>
                {statusTypeLabel[log.statusType]}
              </Typography>
              <StatusChip label={meta.label} tone={meta.tone} en={meta.en} />
            </Stack>
            <Typography variant="body2" sx={{ mt: 0.75, color: text.secondary }}>
              {formatDateTime(log.changedAt)} · {actorLabel(log.actor, applicantName)}
            </Typography>
            {log.detail && (
              <Box sx={{ mt: 1, px: 1.5, py: 1, borderRadius: radius.md, bgcolor: box.bg }}>
                <Stack spacing={0.25}>
                  <Typography variant="subtitle2" sx={{ color: box.fg }}>
                    {rejected ? '반려 사유' : '원인'}
                  </Typography>
                  <Typography variant="body2" sx={{ color: box.fg }}>
                    {log.detail}
                  </Typography>
                </Stack>
              </Box>
            )}
          </Box>
        );
      })}
    </Box>
  );
}
