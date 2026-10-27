import { Box, Stack, Typography } from '@mui/material';
import StatusChip from '@/components/common/StatusChip';
import { color, font, line, radius, text, tones } from '@/theme/theme';
import type { EventMonitoring, MonitoringStepStatus } from '@/types';
import { STEP_TITLE, TEXT, headlineOf, hostingFailed, refreshStatus, serviceChip, soldOutNote, stepCaption } from '../monitoringView';

interface ProgressCardProps {
  m: EventMonitoring;
  /** 서버 기준 지금(ms) */
  nowMs: number;
  /** 데이터 지연(서버 stale 또는 호출 실패) */
  delayed: boolean;
}

/** 끝난 구간은 남색 실선, 남은 구간은 회색 점선 */
const segment = (reached: boolean) =>
  reached ? { borderTop: `2px solid ${color.navy}` } : { borderTop: `2px dashed ${line.base}` };

function Dot({ status }: { status: MonitoringStepStatus }) {
  const base = { width: 12, height: 12, borderRadius: '50%', flexShrink: 0, boxSizing: 'border-box' as const };
  if (status === 'DONE') return <Box sx={{ ...base, bgcolor: color.navy }} />;
  // 실패(호스팅 시작 · 입장)는 주황 — 빨강은 쓰지 않는다
  if (status === 'FAILED') return <Box sx={{ ...base, bgcolor: tones.amber.dot }} />;
  if (status === 'CURRENT') return <Box sx={{ ...base, width: 14, height: 14, bgcolor: color.white, border: `2.5px solid ${color.navy}` }} />;
  return <Box sx={{ ...base, bgcolor: color.white, border: `2px solid ${line.strong}` }} />;
}

/** 진행 단계(회색 카드) — 윗줄: 남은 시간 · 품절 시각 · 서비스 · 갱신 문구 / 아랫줄: 5단계 */
export default function ProgressCard({ m, nowMs, delayed }: ProgressCardProps) {
  const headline = headlineOf(m, nowMs);
  const soldOut = soldOutNote(m, nowMs);
  const service = serviceChip(m.header.service, delayed);
  const refresh = refreshStatus(m, delayed);
  const steps = m.steps;

  return (
    <Box sx={{ bgcolor: color.card, borderRadius: radius.xl, px: { xs: 2.5, md: 4 }, py: 3 }}>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={{ xs: 1.5, md: 3 }} alignItems={{ xs: 'flex-start', md: 'center' }}>
        {headline.delayed ? (
          <Typography variant="body1" sx={{ color: tones.amber.fg, fontWeight: font.weight.semibold }}>
            {headline.label} · {headline.value}
          </Typography>
        ) : (
          <Stack direction="row" spacing={1} alignItems="baseline">
            <Typography variant="body2" sx={{ color: text.tertiary }}>
              {headline.label}
            </Typography>
            <Typography sx={{ fontFamily: font.heading, fontSize: 22, fontWeight: font.weight.bold, color: text.primary }}>{headline.value}</Typography>
            {headline.note && (
              <Typography variant="body2" sx={{ color: text.secondary }}>
                · {headline.note}
              </Typography>
            )}
          </Stack>
        )}
        {soldOut && <StatusChip label={soldOut} tone="amber" />}
        {service && (
          <Stack direction="row" spacing={1} alignItems="center">
            <Typography variant="body2" sx={{ color: text.tertiary }}>
              서비스
            </Typography>
            <StatusChip label={service.label} tone={service.tone} />
          </Stack>
        )}
        <Box sx={{ flex: 1, display: { xs: 'none', md: 'block' } }} />
        {refresh &&
          (refresh.delayed ? (
            <StatusChip label={refresh.text} tone="amber" />
          ) : (
            <Stack direction="row" spacing={0.75} alignItems="center">
              <Box sx={{ width: 6, height: 6, borderRadius: '50%', bgcolor: tones.green.dot }} />
              <Typography variant="body2" sx={{ color: text.tertiary }}>
                {refresh.text}
              </Typography>
            </Stack>
          ))}
      </Stack>

      {/* 호스팅 시작 실패 · 준비 실패 · 지연 중 안내 — 빨강은 쓰지 않는다 */}
      {hostingFailed(m) && (
        <Typography variant="body2" sx={{ mt: 1.5, color: tones.amber.fg, fontWeight: font.weight.semibold }}>
          {TEXT.hostingFailed}
        </Typography>
      )}
      {m.prepareFailed && m.state !== 'ENDED' && (
        <Typography variant="body2" sx={{ mt: 1.5, color: tones.amber.fg, fontWeight: font.weight.semibold }}>
          {TEXT.prepareFailed}
        </Typography>
      )}
      {m.header.service === 'DELAYED' && !delayed && (
        <Typography variant="body2" sx={{ mt: 1.5, color: tones.amber.fg }}>
          {TEXT.serviceDelayed}
        </Typography>
      )}

      <Box component="ol" aria-label="진행 단계" sx={{ listStyle: 'none', p: 0, m: 0, mt: 3, display: 'grid', gridTemplateColumns: `repeat(${steps.length}, 1fr)` }}>
        {steps.map((s, i) => {
          // 앞 단계가 끝났고 이 단계에 들어섰으면(현재 · 실패) 실선. 끝나지 않은 단계(현재 · 실패)의 오른쪽은 점선
          const reachedLeft = i > 0 && steps[i - 1].status === 'DONE' && s.status !== 'UPCOMING';
          const reachedRight = i + 1 < steps.length && s.status === 'DONE' && steps[i + 1].status !== 'UPCOMING';
          return (
            <Box component="li" key={s.step} aria-current={s.status === 'CURRENT' ? 'step' : undefined} sx={{ minWidth: 0, textAlign: 'center' }}>
              <Stack direction="row" alignItems="center" sx={{ height: 16 }}>
                <Box sx={{ flex: 1, ...(i === 0 ? {} : segment(reachedLeft)) }} />
                <Dot status={s.status} />
                <Box sx={{ flex: 1, ...(i === steps.length - 1 ? {} : segment(reachedRight)) }} />
              </Stack>
              <Typography
                variant="body2"
                sx={{
                  mt: 1,
                  color: s.status === 'UPCOMING' ? text.tertiary : text.primary,
                  fontWeight: s.status === 'CURRENT' || s.status === 'FAILED' ? font.weight.bold : font.weight.regular,
                }}
              >
                {STEP_TITLE[s.step]}
              </Typography>
              <Typography
                variant="caption"
                component="p"
                sx={{ mt: 0.25, color: s.status === 'FAILED' ? tones.amber.fg : s.status === 'CURRENT' ? text.secondary : text.tertiary }}
              >
                {stepCaption(s, m, nowMs)}
              </Typography>
            </Box>
          );
        })}
      </Box>
    </Box>
  );
}
