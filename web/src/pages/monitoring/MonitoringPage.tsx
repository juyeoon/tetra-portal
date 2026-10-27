import { useEffect, useRef, useState } from 'react';
import { Link as RouterLink, useSearchParams } from 'react-router-dom';
import { Box, Button, MenuItem, Stack, TextField, Typography } from '@mui/material';
import EventAvailableOutlinedIcon from '@mui/icons-material/EventAvailableOutlined';
import HourglassEmptyRoundedIcon from '@mui/icons-material/HourglassEmptyRounded';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import ShowChartOutlinedIcon from '@mui/icons-material/ShowChartOutlined';
import { getEventMonitoring, listMonitoringEvents } from '@/api/portal';
import { usePolling } from '@/api/usePolling';
import { ErrorView, LoadingView } from '@/components/common/LoadState';
import SectionTitle from '@/components/common/SectionTitle';
import StateMessage from '@/components/common/StateMessage';
import PageHeader from '@/components/layout/PageHeader';
import { font, size, text } from '@/theme/theme';
import type { MonitoringEventItem } from '@/types';
import IntegrationCheck from './components/IntegrationCheck';
import KpiCards from './components/KpiCards';
import PerMinuteChart from './components/PerMinuteChart';
import ProgressCard from './components/ProgressCard';
import StockChart from './components/StockChart';
import { POLL_INTERVAL_MS, TEXT, boundaryToRefetch, estimateNow, itemCaption, periodText, pickEventId, showsMetrics } from './monitoringView';

/** 1초마다 다시 그린다 — 남은 시간 · 경계 시각 확인용 */
function useSecondTick() {
  const [tick, setTick] = useState(0);
  useEffect(() => {
    const timer = window.setInterval(() => setTick((t) => t + 1), 1000);
    return () => window.clearInterval(timer);
  }, []);
  return tick;
}

/**
 * UI-TP-11 모니터링 — 이벤트 실시간 현황(15초마다 갱신되는 근사값, 확정 결과는 결과 탭).
 * 목록 · 현황을 15초마다 같이 새로 고친다(탭이 숨겨지면 멈춤, 종료 이벤트는 현황을 한 번만).
 * 선택은 처음 한 번 정한다(?eventId= → defaultEventId). 직접 고르면 ?eventId=에 남기고, 목록이 새로 와도 바꾸지 않는다.
 * 404(목록에서 빠짐 · 권한 없음)면 ?eventId=를 지우고 새 목록의 defaultEventId로. 401은 client가 로그인 화면으로.
 */
export default function MonitoringPage() {
  const [params, setParams] = useSearchParams();
  const [selected, setSelected] = useState<number | null>(null);
  const [needPick, setNeedPick] = useState(true);
  const list = usePolling(listMonitoringEvents, [], { intervalMs: POLL_INTERVAL_MS });
  const detail = usePolling(() => getEventMonitoring(selected!), [selected], {
    intervalMs: POLL_INTERVAL_MS,
    enabled: selected !== null,
    stopWhen: (d) => d.state === 'ENDED',
  });
  const tick = useSecondTick();
  const fired = useRef(new Set<string>());
  // 드롭다운에서 빠진 이벤트를 보고 있을 때(종료 7일 경과 등) 항목을 남겨 둔다
  const lastItem = useRef<MonitoringEventItem | null>(null);

  // 새 목록이 왔을 때만 고른다 — 404 뒤에는 새로 받은 목록으로, 볼 이벤트가 없다가 생기면 그때
  useEffect(() => {
    if (!list.data || (!needPick && selected !== null)) return;
    setSelected(pickEventId(list.data, params.get('eventId')));
    setNeedPick(false);
  }, [list.receivedAt]);

  useEffect(() => {
    if (detail.error?.status !== 404) return;
    setParams(
      (prev) => {
        const p = new URLSearchParams(prev);
        p.delete('eventId');
        return p;
      },
      { replace: true },
    );
    setSelected(null);
    setNeedPick(true);
    list.refresh();
  }, [detail.error]);

  const m = detail.data;
  const nowMs = m && detail.receivedAt != null ? estimateNow(m.serverTime, performance.now() - detail.receivedAt) : 0;

  // 경계 시각(입장 · 시작 · 종료)을 넘으면 15초를 기다리지 않고 한 번만 다시 부른다
  useEffect(() => {
    if (!m) return;
    const key = boundaryToRefetch(m, nowMs, fired.current);
    if (key) {
      fired.current.add(key);
      detail.refresh();
    }
  }, [tick, m]);

  const choose = (id: number) => {
    setSelected(id);
    setParams(
      (prev) => {
        const p = new URLSearchParams(prev);
        p.set('eventId', String(id));
        return p;
      },
      { replace: true },
    );
  };

  const header = <PageHeader eyebrow="MONITORING" breadcrumbs={['모니터링', '이벤트 실시간 현황']} />;

  if (!list.data) {
    return (
      <>
        {header}
        {list.error && list.error.status !== 401 ? <ErrorView message={list.error.message} onRetry={list.refresh} /> : <LoadingView />}
      </>
    );
  }

  const events = list.data.events;
  const current = events.find((e) => e.eventId === selected) ?? (lastItem.current?.eventId === selected ? lastItem.current : null);
  if (current) lastItem.current = current;
  const options = current && !events.includes(current) ? [current, ...events] : events;

  if (events.length === 0 && selected === null) {
    return (
      <>
        {header}
        <StateMessage icon={<ShowChartOutlinedIcon />} tone="grey" title={TEXT.noEvent} description={TEXT.noEventDescription} />
      </>
    );
  }

  // 호출 실패(네트워크 · 5xx)는 마지막 값을 그대로 두고 데이터 지연으로 — 401 · 404는 위 흐름이 처리
  const failed = detail.error != null && detail.error.status !== 401 && detail.error.status !== 404;
  const delayed = m != null && (m.stale || failed);
  const today = m ? nowMs : Date.now();

  return (
    <>
      {header}

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} alignItems={{ xs: 'stretch', md: 'center' }} sx={{ mb: 3 }}>
        <Stack direction="row" spacing={2} alignItems="center">
          <Typography variant="h4" component="label" htmlFor="monitoring-event" sx={{ whiteSpace: 'nowrap' }}>
            이벤트
          </Typography>
          <TextField
            id="monitoring-event"
            select
            size="small"
            value={selected ?? ''}
            onChange={(e) => choose(Number(e.target.value))}
            sx={{ minWidth: { xs: 0, md: 360 }, flex: { xs: 1, md: 'none' } }}
          >
            {options.map((e) => (
              <MenuItem key={e.eventId} value={e.eventId}>
                {e.name} · {itemCaption(e, today)}
              </MenuItem>
            ))}
          </TextField>
        </Stack>
        {m && (
          <Typography variant="body2" sx={{ color: text.tertiary }}>
            이벤트 기간 {periodText(m.period.startAt, m.period.endAt)}
            {m.period.domain && ` · ${m.period.domain}`}
          </Typography>
        )}
      </Stack>

      {!m ? (
        failed ? <ErrorView message={detail.error!.message} onRetry={detail.refresh} /> : <LoadingView />
      ) : (
        <Stack spacing={4}>
          <ProgressCard m={m} nowMs={nowMs} delayed={delayed} />

          {showsMetrics(m) && m.kpi && m.series ? (
            <>
              <Box>
                <SectionTitle>핵심 지표</SectionTitle>
                <KpiCards m={m} kpi={m.kpi} nowMs={nowMs} />
              </Box>
              <Box>
                <SectionTitle>추이</SectionTitle>
                <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', lg: 'repeat(2, 1fr)' } }}>
                  <PerMinuteChart m={m} series={m.series} />
                  <StockChart m={m} series={m.series} nowMs={nowMs} />
                </Box>
              </Box>
              <IntegrationCheck rejects5m={m.rejects5m} />
            </>
          ) : m.state === 'ENDED' ? (
            <StateMessage icon={<EventAvailableOutlinedIcon />} tone="blue" title={TEXT.ended} description={TEXT.endedDescription}>
              <Button component={RouterLink} to={`/events/${m.eventId}?tab=result`} variant="contained" sx={{ mt: 2 }}>
                결과 보기
              </Button>
            </StateMessage>
          ) : (
            <StateMessage icon={<HourglassEmptyRoundedIcon />} tone="grey" title={TEXT.beforeEntry} />
          )}

          {/* 근사값 안내 — 수치가 보일 때만(종료 · 입장 전은 위 안내가 대신한다) */}
          {showsMetrics(m) && (
            <Stack direction="row" spacing={1} alignItems="flex-start" sx={{ color: text.tertiary }}>
              <InfoOutlinedIcon sx={{ fontSize: size.iconSm, mt: '3px' }} />
              <Typography variant="body2">
                15초마다 갱신되는 근사값이에요. 확정된 발급 결과는 이벤트가 끝나고 집계가 완료되면{' '}
                <Box component="span" sx={{ color: text.primary, fontWeight: font.weight.semibold }}>
                  이벤트 내역 › 결과
                </Box>{' '}
                탭에서 볼 수 있어요.
              </Typography>
            </Stack>
          )}
        </Stack>
      )}
    </>
  );
}
