import { Box, Stack, Typography } from '@mui/material';
import { font, line, size, text } from '@/theme/theme';
import { type ChartAxis, isTick, kstIso } from '../monitoringView';

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

/*
 * 두 그래프의 그림 영역을 같게 — 같은 여백 · 세로축 폭 · 1분 칸(band) 가로축이라 가로 범위 · 눈금 · 기준선이 같은 자리에 온다.
 */
export const CHART_HEIGHT = 260;

export const CHART_MARGIN = { top: 20, right: 24, bottom: 0, left: 0 };

export const Y_AXIS_WIDTH = 64;

/** 1분 칸 가로축 — 칸 = 그 분(구간 시작), 글자는 정각 간격의 배수 분에만 */
export const minuteXAxis = (axis: ChartAxis) => ({
  id: 'x',
  scaleType: 'band' as const,
  data: axis.minutes,
  categoryGapRatio: 0.25,
  valueFormatter: (t: number) => minuteLabel(t),
  tickLabelInterval: (t: number) => isTick(t, axis.tick),
});

/**
 * 툴팁은 포인터 오른쪽 위로(아래 끝을 포인터 높이에) — 기본(오른쪽 아래)은 아래쪽 막대를 가리킬 때 가로축 글자를 덮는다.
 * 오른쪽에 자리가 없으면 왼쪽으로 뒤집힌다(Popper flip)
 */
export const TOOLTIP_PLACEMENT = 'right-end' as const;
