import { Box, Stack, Typography } from '@mui/material';
import { font, line, size, text } from '@/theme/theme';
import { kstIso } from '../monitoringView';

/** 범례 하나 — 막대는 네모, 선은 짧은 선(점선 가능) */
export function Legend({ fill, label, kind = 'box' }: { fill: string; label: string; kind?: 'box' | 'line' | 'dashed' }) {
  const mark =
    kind === 'box' ? { width: size.dot, height: size.dot, bgcolor: fill } : { width: 14, borderTop: `2px ${kind === 'dashed' ? 'dashed' : 'solid'} ${fill}` };
  return (
    <Stack direction="row" spacing={0.75} alignItems="center">
      <Box sx={mark} />
      <Typography variant="body2" sx={{ color: text.secondary }}>
        {label}
      </Typography>
    </Stack>
  );
}

/** 결과 탭 분당 그래프와 같은 축 · 격자 모양 */
export const chartSx = {
  '& .MuiChartsAxis-line, & .MuiChartsAxis-tick': { stroke: 'transparent !important' },
  '& .MuiChartsAxis-tickLabel': { fill: `${text.tertiary} !important`, fontSize: `${font.size.caption} !important` },
  '& .MuiChartsGrid-line': { stroke: line.base },
  '& .MuiChartsReferenceLine-line': { stroke: `${line.strong} !important`, strokeDasharray: '3 3' },
  '& .MuiChartsReferenceLine-label': { fill: `${text.tertiary} !important`, fontSize: `${font.size.caption} !important` },
};

/** 축 · 툴팁 시각 'HH:mm' — 한국 시각 글자 그대로(브라우저 시간대와 무관) */
export const minuteLabel = (ms: number) => kstIso(ms).slice(11, 16);
