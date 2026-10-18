import type { ReactNode } from 'react';
import { Box, Button, Card, Stack, Typography } from '@mui/material';
import { BarChart } from '@mui/x-charts/BarChart';
import FileDownloadOutlinedIcon from '@mui/icons-material/FileDownloadOutlined';
import InsightsOutlinedIcon from '@mui/icons-material/InsightsOutlined';
import SectionTitle from '@/components/common/SectionTitle';
import StatCard from '@/components/common/StatCard';
import StateMessage from '@/components/common/StateMessage';
import { planMeta } from '@/mocks/pricing';
import { chartColors, font, line, size, text } from '@/theme/theme';
import type { EventResult, PortalEvent } from '@/types';
import { totalCouponStock } from '@/utils/event';
import { downloadText, formatNumber, formatPercent } from '@/utils/format';

function Legend({ fill, label }: { fill: string; label: ReactNode }) {
  return (
    <Stack direction="row" spacing={0.75} alignItems="center">
      <Box sx={{ width: size.dot, height: size.dot, bgcolor: fill }} />
      <Typography variant="body2" sx={{ color: text.secondary }}>
        {label}
      </Typography>
    </Stack>
  );
}

// TODO: 발급 이력 CSV API 연동 (발급 이력 전체). Mock은 분당 집계로 대신 만든다
function downloadCsv(event: PortalEvent, r: EventResult) {
  const rows = [['시각(오픈 후)', '발급 성공', '품절'], ...r.perMinute.map((m) => [m.minute, String(m.issued), String(m.soldOut)])];
  downloadText(`${event.id}_발급이력.csv`, rows.map((row) => row.join(',')).join('\n'));
}

/** UI-TP-6-2 이벤트 상세 - 결과 */
export default function EventResultPanel({ event }: { event: PortalEvent }) {
  const r = event.result;

  // 집계 전 안내 — 오픈 전이거나 진행 중인 이벤트
  if (!r) {
    return (
      <StateMessage
        icon={<InsightsOutlinedIcon />}
        tone="grey"
        title="아직 확인할 수 있는 결과가 없습니다"
        description={
          <>
            이벤트 오픈 전이거나 진행 중인 이벤트는 발급 데이터를 집계하지 않습니다.
            <br />
            이벤트가 종료되고 집계가 끝나면 이 탭에서 결과를 확인할 수 있습니다.
          </>
        }
      />
    );
  }

  const stock = totalCouponStock(event.coupons);
  const capacity = planMeta[event.plan].capacity;
  const attempts = r.issued + r.soldOut + r.dropped;
  const parts = [
    { label: '발급 성공', value: r.issued, fill: chartColors.issued },
    { label: '품절', value: r.soldOut, fill: chartColors.soldOut },
    { label: '이탈', value: r.dropped, fill: chartColors.dropped },
  ];

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', sm: 'row' }} alignItems={{ xs: 'flex-start', sm: 'center' }} justifyContent="flex-end" spacing={1.5}>
        <Typography variant="body2" sx={{ color: text.tertiary }}>
          이벤트 오픈 {r.openedAt} · 집계 기준 {r.aggregatedAt}
        </Typography>
        <Button size="small" variant="outlined" startIcon={<FileDownloadOutlinedIcon />} onClick={() => downloadCsv(event, r)}>
          발급 이력 CSV 다운로드
        </Button>
      </Stack>

      <Box>
        <SectionTitle>핵심 지표</SectionTitle>
        <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', md: 'repeat(3, 1fr)' } }}>
          <StatCard label="발급 성공 / 총 재고" value={`${formatNumber(r.issued)} / ${formatNumber(stock)}`} sub={`소진율 ${formatPercent(r.issued, stock, 0)}`} />
          <StatCard label="품절로 못 받은 수" value={`${formatNumber(r.soldOut)}명`} sub="재고 소진 후 발급 시도" />
          <StatCard
            label="최대 동시 접속자 / 플랜 수용 인원"
            value={`${formatNumber(r.peakConcurrent)} / ${formatNumber(capacity)}`}
            sub={`사용률 ${formatPercent(r.peakConcurrent, capacity)} · ${r.peakAt}`}
          />
        </Box>
      </Box>

      <Card sx={{ p: 3 }}>
        <SectionTitle>발급 결과 구성</SectionTitle>
        <Typography variant="body2" sx={{ mt: -1.5, mb: 1.5, color: text.tertiary }}>
          전체 발급 시도 {formatNumber(attempts)}건
        </Typography>
        <Stack direction="row" sx={{ height: 10, overflow: 'hidden', gap: '2px' }}>
          {parts.map((p) => (
            <Box key={p.label} sx={{ width: `${(p.value / attempts) * 100}%`, bgcolor: p.fill }} title={p.label} />
          ))}
        </Stack>
        <Stack direction="row" flexWrap="wrap" useFlexGap spacing={3} sx={{ mt: 1.5 }}>
          {parts.map((p) => (
            <Legend
              key={p.label}
              fill={p.fill}
              label={
                <>
                  {p.label}{' '}
                  <Box component="span" sx={{ color: text.primary, fontWeight: font.weight.semibold }}>
                    {formatNumber(p.value)}건
                  </Box>{' '}
                  ({formatPercent(p.value, attempts)})
                </>
              }
            />
          ))}
        </Stack>
      </Card>

      <Card sx={{ p: 3 }}>
        <SectionTitle
          action={
            <Stack direction="row" spacing={2}>
              <Legend fill={chartColors.issued} label="발급 성공" />
              <Legend fill={chartColors.soldOut} label="품절" />
            </Stack>
          }
        >
          분당 발급 결과 (대기열 진입 시각 기준)
        </SectionTitle>
        <BarChart
          height={300}
          hideLegend
          grid={{ horizontal: true }}
          margin={{ top: 16, right: 0, bottom: 0, left: 0 }}
          xAxis={[{ scaleType: 'band', data: r.perMinute.map((m) => m.minute), categoryGapRatio: 0.5 }]}
          yAxis={[{ width: 64, valueFormatter: (v: number) => formatNumber(v) }]}
          series={[
            { data: r.perMinute.map((m) => m.issued), label: '발급 성공', stack: 'total', color: chartColors.issued, valueFormatter: (v) => `${formatNumber(v ?? 0)}건` },
            { data: r.perMinute.map((m) => m.soldOut), label: '품절', stack: 'total', color: chartColors.soldOut, valueFormatter: (v) => `${formatNumber(v ?? 0)}건` },
          ]}
          sx={{
            '& .MuiChartsAxis-line, & .MuiChartsAxis-tick': { stroke: 'transparent !important' },
            '& .MuiChartsAxis-tickLabel': { fill: `${text.tertiary} !important`, fontSize: `${font.size.caption} !important` },
            '& .MuiChartsGrid-line': { stroke: line.base },
          }}
        />
      </Card>
    </Stack>
  );
}
