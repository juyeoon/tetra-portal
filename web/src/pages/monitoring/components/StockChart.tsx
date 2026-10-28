import { Card, Stack, Typography } from '@mui/material';
import { ChartContainer } from '@mui/x-charts/ChartContainer';
import { ChartsAxisHighlight } from '@mui/x-charts/ChartsAxisHighlight';
import { ChartsGrid } from '@mui/x-charts/ChartsGrid';
import { ChartsReferenceLine } from '@mui/x-charts/ChartsReferenceLine';
import { ChartsTooltip } from '@mui/x-charts/ChartsTooltip';
import { ChartsXAxis } from '@mui/x-charts/ChartsXAxis';
import { ChartsYAxis } from '@mui/x-charts/ChartsYAxis';
import { LinePlot, MarkPlot } from '@mui/x-charts/LineChart';
import SectionTitle from '@/components/common/SectionTitle';
import { chartColors, text } from '@/theme/theme';
import type { EventMonitoring, MonitoringSeries } from '@/types';
import { formatNumber } from '@/utils/format';
import { chartAxis, minuteSlot, stockChart, stockSubtitle } from '../monitoringView';
import { CHART_HEIGHT, CHART_MARGIN, Legend, TOOLTIP_PLACEMENT, Y_AXIS_WIDTH, chartSx, minuteXAxis } from './chartParts';

/**
 * 남은 재고 · 소진 예상 — 재고 계단(칸 = 그 분에 읽은 값, 분당 막대와 같은 칸) + 소진 예상 점선(직선).
 * 품절이면 예상 대신 품절 칸(주황 점). 가로축은 분당 그래프와 같은 1분 칸이라 범위 · 눈금이 맞는다
 */
export default function StockChart({ m, series, nowMs }: { m: EventMonitoring; series: MonitoringSeries; nowMs: number }) {
  const soldOut = m.state === 'SOLD_OUT';
  const axis = chartAxis(series, m.state, m.period.endAt);
  const d = stockChart(series, m.state, m.header.soldOutAt, axis);
  const startAt = minuteSlot(axis, m.period.startAt);
  const count = (v: number | null) => (v == null ? null : `${formatNumber(v)}개`);

  return (
    <Card sx={{ p: 3 }}>
      <SectionTitle
        mb={0.5}
        action={
          <Stack direction="row" spacing={2}>
            <Legend fill={chartColors.issued} label="남은 재고" kind="line" />
            {soldOut && <Legend fill={chartColors.soldOut} label="품절" />}
            {d.hasProjection && <Legend fill={chartColors.issued} label="예상" kind="dashed" />}
          </Stack>
        }
      >
        {soldOut ? '남은 재고' : '남은 재고 · 소진 예상'}
      </SectionTitle>
      <Typography variant="body2" sx={{ mb: 1, color: text.tertiary }}>
        {stockSubtitle(m, nowMs)}
      </Typography>
      {axis.minutes.length === 0 ? (
        <Typography variant="body2" sx={{ py: 6, textAlign: 'center', color: text.tertiary }}>
          아직 기록이 없어요.
        </Typography>
      ) : (
        <ChartContainer
          height={CHART_HEIGHT}
          margin={CHART_MARGIN}
          xAxis={[minuteXAxis(axis)]}
          yAxis={[{ id: 'y', width: Y_AXIS_WIDTH, min: 0, valueFormatter: (v: number) => formatNumber(v) }]}
          series={[
            // 계단 — 분 사이를 곡선으로 잇지 않는다(칸 하나 = 그 분의 값)
            { type: 'line', id: 'remaining', label: '남은 재고', color: chartColors.issued, curve: 'step', data: d.remaining, showMark: false, valueFormatter: count },
            ...(d.hasProjection
              ? [{ type: 'line' as const, id: 'projection', label: '예상', color: chartColors.issued, curve: 'linear' as const, connectNulls: true, data: d.projection, showMark: false, valueFormatter: count }]
              : []),
            ...(soldOut ? [{ type: 'line' as const, id: 'soldOut', label: '품절', color: chartColors.soldOut, data: d.soldOut, showMark: true, valueFormatter: count }] : []),
          ]}
          sx={{
            ...chartSx,
            '& .MuiLineElement-root': { strokeWidth: 2 },
            '& .MuiLineElement-root[data-series="projection"]': { strokeDasharray: '6 4' },
          }}
        >
          <ChartsGrid horizontal />
          <LinePlot />
          <MarkPlot />
          {startAt != null && <ChartsReferenceLine x={startAt} label="시작" labelAlign="start" />}
          {d.hasProjection && d.lastActual != null && <ChartsReferenceLine x={d.lastActual} label="지금" labelAlign="start" />}
          <ChartsXAxis />
          <ChartsYAxis />
          <ChartsAxisHighlight x="band" />
          <ChartsTooltip trigger="axis" placement={TOOLTIP_PLACEMENT} />
        </ChartContainer>
      )}
    </Card>
  );
}
