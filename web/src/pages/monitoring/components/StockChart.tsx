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
import { stockChart, stockSubtitle, tickMinutes, timeTicks } from '../monitoringView';
import { Legend, chartSx, minuteLabel } from './chartParts';

/** 남은 재고 · 소진 예상 — 재고 선 + 소진 예상 점선. 품절이면 예상 대신 품절 지점(주황 점) */
export default function StockChart({ m, series, nowMs }: { m: EventMonitoring; series: MonitoringSeries; nowMs: number }) {
  const soldOut = m.state === 'SOLD_OUT';
  const d = stockChart(series, m.state, m.header.soldOutAt);
  const start = new Date(m.period.startAt);
  const lastActual = series.stock.length > 0 ? new Date(series.stock[series.stock.length - 1].t) : null;
  const count = (v: number | null) => (v == null ? null : `${formatNumber(v)}개`);
  // 분당 그래프와 같은 정각 간격 — 소진 예상 시각까지(데이터 없는 점선 구간 포함)
  const from = d.x[0] ?? 0;
  const to = d.x[d.x.length - 1] ?? 0;
  const ticks = timeTicks(from, to, tickMinutes((to - from) / 60_000 + 1)).map((t) => new Date(t));

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
      {d.x.length === 0 ? (
        <Typography variant="body2" sx={{ py: 6, textAlign: 'center', color: text.tertiary }}>
          아직 기록이 없어요.
        </Typography>
      ) : (
        <ChartContainer
          height={260}
          margin={{ top: 20, right: 28, bottom: 0, left: 0 }}
          xAxis={[{ id: 'x', scaleType: 'time', data: d.x.map((t) => new Date(t)), valueFormatter: (v: Date) => minuteLabel(v.getTime()) }]}
          yAxis={[{ id: 'y', width: 64, min: 0, valueFormatter: (v: number) => formatNumber(v) }]}
          series={[
            { type: 'line', id: 'remaining', label: '남은 재고', color: chartColors.issued, data: d.remaining, showMark: false, valueFormatter: count },
            ...(d.hasProjection
              ? [{ type: 'line' as const, id: 'projection', label: '예상', color: chartColors.issued, data: d.projection, showMark: false, valueFormatter: count }]
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
          {start.getTime() >= d.x[0] && start.getTime() <= d.x[d.x.length - 1] && <ChartsReferenceLine x={start} label="시작" labelAlign="start" />}
          {d.hasProjection && lastActual && <ChartsReferenceLine x={lastActual} label="지금" labelAlign="start" />}
          <ChartsXAxis tickInterval={ticks} />
          <ChartsYAxis />
          <ChartsAxisHighlight x="line" />
          <ChartsTooltip trigger="axis" />
        </ChartContainer>
      )}
    </Card>
  );
}
