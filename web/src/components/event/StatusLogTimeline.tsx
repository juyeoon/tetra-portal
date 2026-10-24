import { Box, Stack, Typography } from '@mui/material';
import StatusChip from '@/components/common/StatusChip';
import { line, radius, text, tones } from '@/theme/theme';
import type { StatusLogItem } from './statusLogView';

/**
 * 상태 처리 기록 (Status Log) — 신청 · 호스팅 · 이벤트 상태 전이와 이벤트 시작(화면이 만드는 항목)을 시각순으로.
 * 문구 · 순서는 statusLogView.timelineOf(Console과 같은 규칙), 배치는 Portal 세로형.
 */
export default function StatusLogTimeline({ logs }: { logs: StatusLogItem[] }) {
  if (logs.length === 0) {
    return (
      <Typography variant="body2" sx={{ color: text.tertiary }}>
        기록이 없습니다.
      </Typography>
    );
  }
  return (
    <Box component="ol" sx={{ listStyle: 'none', m: 0, p: 0 }}>
      {logs.map((item, i) => {
        const last = i === logs.length - 1;
        const box = item.detail?.rejected ? tones.red : tones.grey;
        return (
          <Box component="li" key={item.key} sx={{ position: 'relative', pl: 3, pb: last ? 0 : 2.5 }}>
            {/* 점 + 세로선 — 아직 오지 않은 일정(이벤트 시작 예정)은 회색 */}
            <Box sx={{ position: 'absolute', left: 3, top: 9, width: 9, height: 9, bgcolor: tones[item.status.tone].dot }} />
            {!last && <Box sx={{ position: 'absolute', left: 7, top: 22, bottom: 2, width: '1px', bgcolor: line.base }} />}

            <Stack direction="row" spacing={0.75} alignItems="center" sx={{ flexWrap: 'wrap', rowGap: 0.5 }}>
              <Typography variant="body2" sx={{ color: text.tertiary }}>
                {item.typeLabel}
              </Typography>
              <Typography variant="subtitle2" sx={{ color: item.planned ? text.secondary : text.primary }}>
                {item.title}
              </Typography>
              <StatusChip label={item.status.label} tone={item.status.tone} en={item.status.en} />
            </Stack>
            <Typography variant="body2" sx={{ mt: 0.75, color: text.secondary }}>
              {item.at} · {item.actor}
            </Typography>
            {item.description && (
              <Typography variant="body2" sx={{ mt: 0.5, color: text.secondary }}>
                {item.description}
              </Typography>
            )}
            {item.detail && (
              <Box sx={{ mt: 1, px: 1.5, py: 1, borderRadius: radius.md, bgcolor: box.bg }}>
                <Stack spacing={0.25}>
                  <Typography variant="subtitle2" sx={{ color: box.fg }}>
                    {item.detail.label}
                  </Typography>
                  <Typography variant="body2" sx={{ color: box.fg }}>
                    {item.detail.text}
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
