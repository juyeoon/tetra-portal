import type { ReactNode } from 'react';
import { Box, IconButton, Stack, Tooltip, Typography } from '@mui/material';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import StatCard from '@/components/common/StatCard';
import { chartColors, font, ink, line, size, text } from '@/theme/theme';
import type { EventMonitoring, MonitoringKpi } from '@/types';
import { PEAK_CONCURRENT_HELP } from '@/utils/event';
import { TEXT, clockOf, countText, share, shareText, waitText } from '../monitoringView';

function Meter({ value }: { value: number }) {
  return (
    <Box role="presentation" sx={{ mt: 2, height: 8, bgcolor: ink[10], overflow: 'hidden' }}>
      <Box sx={{ width: `${value * 100}%`, height: '100%', bgcolor: chartColors.issued }} />
    </Box>
  );
}

function Footer({ label, value }: { label: ReactNode; value: ReactNode }) {
  return (
    <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={2} sx={{ mt: 2, pt: 1.5, borderTop: `1px solid ${line.base}` }}>
      <Typography variant="body2" component="div" sx={{ color: text.secondary }}>
        {label}
      </Typography>
      <Typography variant="body2" sx={{ color: text.primary, fontWeight: font.weight.semibold, whiteSpace: 'nowrap' }}>
        {value}
      </Typography>
    </Stack>
  );
}

function Sub({ children }: { children: ReactNode }) {
  return (
    <Typography variant="body2" sx={{ mt: 1, color: text.tertiary }}>
      {children}
    </Typography>
  );
}

/** 핵심 지표 3개 — 발급 성공 / 총 재고 · 대기 인원(추정) · 접속 중 / 플랜 수용 인원 */
export default function KpiCards({ m, kpi, nowMs }: { m: EventMonitoring; kpi: MonitoringKpi; nowMs: number }) {
  const peak = kpi.peak;
  return (
    <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', md: 'repeat(3, 1fr)' } }}>
      <StatCard label="발급 성공 / 총 재고" value={`${countText(kpi.issued)} / ${countText(kpi.stock)}`}>
        <Meter value={share(kpi.issued, kpi.stock)} />
        <Sub>소진율 {shareText(kpi.issued, kpi.stock)}</Sub>
        <Footer label="품절 응답" value={countText(kpi.soldOutCount, '건')} />
      </StatCard>

      <StatCard label="대기 인원 (추정)" value={countText(kpi.waiting, '명')}>
        <Sub>{TEXT.waitingDescription}</Sub>
        <Footer label="예상 대기 시간" value={waitText(m.state, kpi.waitMinutes)} />
      </StatCard>

      <StatCard label="접속 중 / 플랜 수용 인원" value={`${countText(kpi.activeSessions)} / ${countText(kpi.planCapacity)}`}>
        <Meter value={share(kpi.activeSessions, kpi.planCapacity)} />
        <Sub>
          사용률 {shareText(kpi.activeSessions, kpi.planCapacity)} · {kpi.planName} 플랜
        </Sub>
        <Footer
          label={
            <>
              최고 동시 접속
              {/* 결과 탭과 같은 설명 — 버튼이라 Tab 포커스로도 열린다 */}
              <Tooltip title={PEAK_CONCURRENT_HELP} enterTouchDelay={0}>
                <IconButton size="small" aria-label="최고 동시 접속 설명" sx={{ ml: 0.5, p: 0.25, verticalAlign: 'text-bottom', color: 'inherit' }}>
                  <InfoOutlinedIcon sx={{ fontSize: size.iconSm }} />
                </IconButton>
              </Tooltip>
            </>
          }
          value={peak?.value == null ? countText(null) : `${countText(peak.value, '명')} · ${clockOf(peak.at, nowMs)}`}
        />
      </StatCard>
    </Box>
  );
}
