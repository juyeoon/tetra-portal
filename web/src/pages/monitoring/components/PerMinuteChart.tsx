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
import { chartAxis, minuteSlot, onAxis, perMinuteSubtitle } from '../monitoringView';
import { CHART_HEIGHT, CHART_MARGIN, Legend, TOOLTIP_PLACEMENT, Y_AXIS_WIDTH, chartSx, minuteXAxis } from './chartParts';

/**
 * 분당 입장 · 발급 결과 — 성공 · 품절 누적 막대 + 입장(신규 세션) 직선. 입장 칸 ~ 지금 칸(칸 = 그 분, 마지막 칸은 지금까지).
 * 가로축은 남은 재고 그래프와 같은 칸(소진 예상까지 늘린 칸은 비어 있다).
 * 입장 선은 chartColors.neutral(ink-10)이 흰 카드에서 거의 보이지 않아 이 그래프에서만 text.tertiary(범례도 같이)
 */
export default function PerMinuteChart({ m, series }: { m: EventMonitoring; series: MonitoringSeries }) {
  const axis = chartAxis(series, m.state, m.period.endAt);
  const points = series.perMinute;
  const entryAt = minuteSlot(axis, m.period.entryAt);
  const startAt = minuteSlot(axis, m.period.startAt);
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
        {perMinuteSubtitle(series, m.state, m.lastUpdatedAt ?? m.serverTime) ?? '-'}
      </Typography>
      {points.length === 0 ? (
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
            { type: 'bar', id: 'success', label: '발급 성공', stack: 'claims', color: chartColors.issued, data: onAxis(points.map((p) => p.success), axis), valueFormatter: count('건') },
            { type: 'bar', id: 'soldOut', label: '품절', stack: 'claims', color: chartColors.soldOut, data: onAxis(points.map((p) => p.soldOut), axis), valueFormatter: count('건') },
            // 곡선 보간 없이 점과 점을 직선으로
            { type: 'line', id: 'entries', label: '입장', color: text.tertiary, curve: 'linear', data: onAxis(points.map((p) => p.entries), axis), valueFormatter: count('명') },
          ]}
          sx={{ ...chartSx, '& .MuiLineElement-root': { strokeWidth: 2 } }}
        >
          <ChartsGrid horizontal />
          <BarPlot />
          <LinePlot />
          {entryAt != null && <ChartsReferenceLine x={entryAt} label="입장" labelAlign="start" />}
          {startAt != null && <ChartsReferenceLine x={startAt} label="시작" labelAlign="start" />}
          <ChartsXAxis />
          <ChartsYAxis />
          <ChartsAxisHighlight x="band" />
          <ChartsTooltip trigger="axis" placement={TOOLTIP_PLACEMENT} />
        </ChartContainer>
      )}
    </Card>
  );
}
