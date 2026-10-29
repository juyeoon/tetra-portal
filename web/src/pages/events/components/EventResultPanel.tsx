import { useState, type ReactNode } from 'react';
import { Box, Button, Card, IconButton, Stack, Tooltip, Typography } from '@mui/material';
import { BarChart } from '@mui/x-charts/BarChart';
import FileDownloadOutlinedIcon from '@mui/icons-material/FileDownloadOutlined';
import HourglassEmptyRoundedIcon from '@mui/icons-material/HourglassEmptyRounded';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import InsightsOutlinedIcon from '@mui/icons-material/InsightsOutlined';
import { errorMessage } from '@/api/client';
import { getHistoryCsv, getResult } from '@/api/portal';
import { useApi } from '@/api/useApi';
import { ErrorView, LoadingView } from '@/components/common/LoadState';
import SectionTitle from '@/components/common/SectionTitle';
import StatCard from '@/components/common/StatCard';
import StateMessage from '@/components/common/StateMessage';
import { useToast } from '@/store/ToastContext';
import { chartColors, font, line, size, text } from '@/theme/theme';
import { PEAK_CONCURRENT_HELP } from '@/utils/event';
import { formatDateTime, formatNumber, formatPercent, formatRate, formatTime, openDownload } from '@/utils/format';
import { CHECKING_NOTE } from '@/utils/status';

/** 분당 그래프는 오픈 직후 10분만 그린다 (UI-TP-6-2) */
const CHART_MINUTES = 10;

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

/** UI-TP-6-2 이벤트 상세 - 결과. Console OPS-04-3 과 같은 응답 · 같은 판정 */
export default function EventResultPanel({ eventId }: { eventId: number }) {
  const toast = useToast();
  const { data, error, reload } = useApi(() => getResult(eventId), [eventId]);
  const [downloading, setDownloading] = useState(false);

  // 발급 이력 CSV — 5분짜리 다운로드 링크를 받아 바로 연다
  const downloadCsv = async () => {
    setDownloading(true);
    try {
      openDownload((await getHistoryCsv(eventId)).url);
    } catch (e) {
      toast(errorMessage(e), 'error');
    } finally {
      setDownloading(false);
    }
  };

  if (error) return <ErrorView message={error.message} onRetry={reload} />;
  if (!data) return <LoadingView />;

  // 종료 처리가 끝나지 않음 — 고객 화면은 확인 중(원인은 서버가 내리지 않는다). 다시 처리되면 결과가 보인다
  if (data.state === 'END_FAILED') {
    return (
      <StateMessage
        icon={<HourglassEmptyRoundedIcon />}
        tone="amber"
        title="결과를 확인하고 있습니다"
        description={`${CHECKING_NOTE} 확인이 끝나면 이 탭에서 결과와 발급 이력 CSV를 확인할 수 있습니다.`}
      />
    );
  }

  if (data.state === 'AGGREGATING') {
    return (
      <StateMessage
        icon={<HourglassEmptyRoundedIcon />}
        tone="blue"
        title="발급 결과를 집계하고 있습니다"
        description="이벤트가 끝났습니다. 집계가 끝나면 이 탭에서 결과와 발급 이력 CSV를 확인할 수 있습니다."
      >
        <Button variant="outlined" onClick={reload} sx={{ mt: 2 }}>
          새로고침
        </Button>
      </StateMessage>
    );
  }

  const r = data.result;
  if (data.state === 'IN_PROGRESS' || !r) {
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

  const m = r.metrics;
  const c = r.composition;
  // 이탈이 세션 기준(입장했지만 결과를 받지 못한 수)이라 셋의 합 = 입장 세션 수
  const sessions = c.successCount + c.soldoutCount + c.abandonedCount;
  const parts = [
    { label: '발급 성공', value: c.successCount, fill: chartColors.issued },
    { label: '품절', value: c.soldoutCount, fill: chartColors.soldOut },
    { label: '이탈', value: c.abandonedCount, fill: chartColors.dropped },
  ];
  const minutes = r.perMinute.slice(0, CHART_MINUTES);

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', sm: 'row' }} alignItems={{ xs: 'flex-start', sm: 'center' }} justifyContent="flex-end" spacing={1.5}>
        <Typography variant="body2" sx={{ color: text.tertiary }}>
          이벤트 오픈 {formatDateTime(data.eventOpenAt)} · 집계 기준 {formatDateTime(data.aggregatedAt)}
        </Typography>
        <Button size="small" variant="outlined" startIcon={<FileDownloadOutlinedIcon />} onClick={downloadCsv} disabled={downloading}>
          발급 이력 CSV 다운로드
        </Button>
      </Stack>

      <Box>
        <SectionTitle>핵심 지표</SectionTitle>
        <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', md: 'repeat(3, 1fr)' } }}>
          <StatCard label="발급 성공 / 총 재고" value={`${formatNumber(m.successCount)} / ${formatNumber(m.totalStock)}`} sub={`소진율 ${formatRate(m.selloutRate, 0)}`} />
          <StatCard label="품절로 못 받은 수" value={`${formatNumber(m.soldoutCount)}명`} sub="재고 소진 후 발급 시도" />
          <StatCard
            label={
              <>
                최대 동시 접속자 / 플랜 수용 인원
                {/* 버튼이라 Tab 포커스로도 툴팁이 열린다. 터치는 탭하면 바로 */}
                <Tooltip title={PEAK_CONCURRENT_HELP} enterTouchDelay={0}>
                  <IconButton size="small" aria-label="최대 동시 접속자 설명" sx={{ ml: 0.5, p: 0.25, verticalAlign: 'text-bottom', color: 'inherit' }}>
                    <InfoOutlinedIcon sx={{ fontSize: size.iconSm }} />
                  </IconButton>
                </Tooltip>
              </>
            }
            value={`${m.peakConcurrent == null ? '-' : formatNumber(m.peakConcurrent)} / ${formatNumber(m.maxCapacity)}`}
            sub={`사용률 ${formatRate(m.capacityUsageRate)}${m.peakAt ? ` · ${formatDateTime(m.peakAt)}` : ''}`}
          />
        </Box>
      </Box>

      <Card sx={{ p: 3 }}>
        <SectionTitle>발급 결과 구성</SectionTitle>
        <Typography variant="body2" sx={{ mt: -1.5, mb: 1.5, color: text.tertiary }}>
          전체 입장 세션 {formatNumber(sessions)}건
        </Typography>
        {sessions > 0 && (
          <Stack direction="row" sx={{ height: 10, overflow: 'hidden', gap: '2px' }}>
            {parts.map((p) => (
              <Box key={p.label} sx={{ width: `${(p.value / sessions) * 100}%`, bgcolor: p.fill }} title={p.label} />
            ))}
          </Stack>
        )}
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
                  ({formatPercent(p.value, sessions)})
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
        {minutes.length === 0 ? (
          <Typography variant="body2" sx={{ color: text.tertiary }}>
            발급 기록이 없습니다.
          </Typography>
        ) : (
          <BarChart
            height={300}
            hideLegend
            grid={{ horizontal: true }}
            margin={{ top: 16, right: 0, bottom: 0, left: 0 }}
            xAxis={[{ scaleType: 'band', data: minutes.map((b) => formatTime(b.minute)), categoryGapRatio: 0.5 }]}
            yAxis={[{ width: 64, valueFormatter: (v: number) => formatNumber(v) }]}
            series={[
              { data: minutes.map((b) => b.successCount), label: '발급 성공', stack: 'total', color: chartColors.issued, valueFormatter: (v) => `${formatNumber(v ?? 0)}건` },
              { data: minutes.map((b) => b.soldoutCount), label: '품절', stack: 'total', color: chartColors.soldOut, valueFormatter: (v) => `${formatNumber(v ?? 0)}건` },
            ]}
            sx={{
              '& .MuiChartsAxis-line, & .MuiChartsAxis-tick': { stroke: 'transparent !important' },
              '& .MuiChartsAxis-tickLabel': { fill: `${text.tertiary} !important`, fontSize: `${font.size.caption} !important` },
              '& .MuiChartsGrid-line': { stroke: line.base },
            }}
          />
        )}
      </Card>
    </Stack>
  );
}
