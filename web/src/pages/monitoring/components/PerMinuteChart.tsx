import { Card, Stack, Typography } from '@mui/material';
import { BarPlot } from '@mui/x-charts/BarChart';
import { ChartContainer } from '@mui/x-charts/ChartContainer';
import { ChartsAxisHighlight } from '@mui/x-charts/ChartsAxisHighlight';
import { ChartsGrid } from '@mui/x-charts/ChartsGrid';
import { ChartsReferenceLine } from '@mui/x-charts/ChartsReferenceLine';
import { ChartsTooltip } from '@mui/x-charts/ChartsTooltip';
import { ChartsXAxis } from '@mui/x-charts/ChartsXAxis';
import { ChartsYAxis } from '@mui/x-charts/ChartsYAxis';
import { LinePlot } from '@mui/x-charts/LineChart';
import SectionTitle from '@/components/common/SectionTitle';
import { chartColors, text } from '@/theme/theme';
import type { EventMonitoring, MonitoringSeries } from '@/types';
import { formatNumber } from '@/utils/format';
import { isTick, minuteSlot, perMinuteSubtitle, tickMinutes } from '../monitoringView';
import { Legend, chartSx, minuteLabel } from './chartParts';

/**
 * 분당 입장 · 발급 결과 — 성공 · 품절 누적 막대 + 입장(신규 세션) 선. 입장 시각 ~ 지금.
 * 입장 선은 chartColors.neutral(ink-10)이 흰 카드에서 거의 보이지 않아 이 그래프에서만 text.tertiary(범례도 같이)
 */
export default function PerMinuteChart({ m, series }: { m: EventMonitoring; series: MonitoringSeries }) {
  const points = series.perMinute;
  const step = tickMinutes(points.length);
  const entryAt = minuteSlot(points, m.period.entryAt);
  const startAt = minuteSlot(points, m.period.startAt);
  const count = (unit: string) => (v: number | null) => (v == null ? '-' : `${formatNumber(v)}${unit}`);

  return (
    <Card sx={{ p: 3 }}>
      <SectionTitle
        mb={0.5}
        action={
          <Stack direction="row" spacing={2}>
            <Legend fill={chartColors.issued} label="발급 성공" />
            <Legend fill={chartColors.soldOut} label="품절" />
            <Legend fill={text.tertiary} label="입장" kind="line" />
          </Stack>
        }
      >
        분당 입장 · 발급 결과
      </SectionTitle>
      <Typography variant="body2" sx={{ mb: 1, color: text.tertiary }}>
        {perMinuteSubtitle(series, m.state) ?? '-'}
      </Typography>
      {points.length === 0 ? (
        <Typography variant="body2" sx={{ py: 6, textAlign: 'center', color: text.tertiary }}>
          아직 기록이 없어요.
        </Typography>
      ) : (
        <ChartContainer
          height={260}
          margin={{ top: 20, right: 16, bottom: 0, left: 0 }}
          xAxis={[
            {
              id: 'x',
              scaleType: 'band',
              data: points.map((p) => p.t),
              categoryGapRatio: 0.25,
              valueFormatter: (t: string) => minuteLabel(new Date(t).getTime()),
              tickLabelInterval: (t: string) => isTick(t, step),
            },
          ]}
          yAxis={[{ id: 'y', width: 56, min: 0, valueFormatter: (v: number) => formatNumber(v) }]}
          series={[
            { type: 'bar', id: 'success', label: '발급 성공', stack: 'claims', color: chartColors.issued, data: points.map((p) => p.success), valueFormatter: count('건') },
            { type: 'bar', id: 'soldOut', label: '품절', stack: 'claims', color: chartColors.soldOut, data: points.map((p) => p.soldOut), valueFormatter: count('건') },
            { type: 'line', id: 'entries', label: '입장', color: text.tertiary, data: points.map((p) => p.entries), valueFormatter: count('명') },
          ]}
          sx={{ ...chartSx, '& .MuiLineElement-root': { strokeWidth: 2 } }}
        >
          <ChartsGrid horizontal />
          <BarPlot />
          <LinePlot />
          {entryAt && <ChartsReferenceLine x={entryAt} label="입장" labelAlign="start" />}
          {startAt && <ChartsReferenceLine x={startAt} label="시작" labelAlign="start" />}
          <ChartsXAxis />
          <ChartsYAxis />
          <ChartsAxisHighlight x="band" />
          <ChartsTooltip trigger="axis" />
        </ChartContainer>
      )}
    </Card>
  );
}
