import { Box, Stack, Typography } from '@mui/material';
import StatusChip from '@/components/common/StatusChip';
import { line, radius, text, tones } from '@/theme/theme';
import type { StatusLog } from '@/types';
import { applicationStatusMeta } from '@/utils/status';

/**
 * 상태 처리 기록 (Status Log) — 단계별 타임스탬프와 처리 내용, 반려 시 반려 사유.
 * 콘솔 OPS-03-2·04-2 "신청 내용"에서도 그대로 쓸 수 있도록 PortalEvent에 의존하지 않는다.
 */
export default function StatusLogTimeline({ logs }: { logs: StatusLog[] }) {
  return (
    <Box component="ol" sx={{ listStyle: 'none', m: 0, p: 0 }}>
      {logs.map((log, i) => {
        const meta = applicationStatusMeta[log.status];
        const last = i === logs.length - 1;
        return (
          <Box component="li" key={`${log.status}-${log.at}`} sx={{ position: 'relative', pl: 3, pb: last ? 0 : 2.5 }}>
            {/* 점 + 세로선 */}
            <Box sx={{ position: 'absolute', left: 3, top: 9, width: 9, height: 9, bgcolor: tones[meta.tone].dot }} />
            {!last && <Box sx={{ position: 'absolute', left: 7, top: 22, bottom: 2, width: '1px', bgcolor: line.base }} />}

            <StatusChip label={meta.label} tone={meta.tone} en={meta.en} />
            <Typography variant="body2" sx={{ mt: 0.75, color: text.secondary }}>
              {log.at} · {log.by}
            </Typography>
            {log.reason && (
              <Box sx={{ mt: 1, px: 1.5, py: 1, borderRadius: radius.md, bgcolor: tones.red.bg }}>
                <Stack spacing={0.25}>
                  <Typography variant="subtitle2" sx={{ color: tones.red.fg }}>
                    반려 사유
                  </Typography>
                  <Typography variant="body2" sx={{ color: tones.red.fg }}>
                    {log.reason}
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
