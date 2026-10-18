import { Link as RouterLink } from 'react-router-dom';
import { Box, Card, Link, Stack, Typography } from '@mui/material';
import ArrowBackRoundedIcon from '@mui/icons-material/ArrowBackRounded';
import CopyButton from '@/components/common/CopyButton';
import StatusChip, { EmptyValue } from '@/components/common/StatusChip';
import { size, text, type } from '@/theme/theme';
import type { PortalEvent } from '@/types';
import { formatPeriod } from '@/utils/format';
import { applicationStatusMeta } from '@/utils/status';

/** 이벤트 상세 상단 요약 — 탭을 바꿔도 항상 보이는 영역 (UI-TP-06 ①) */
export default function EventSummary({ event }: { event: PortalEvent }) {
  const status = applicationStatusMeta[event.applicationStatus];
  const facts = [
    { label: '이벤트 ID', value: <Box sx={{ ...type.monoSm, wordBreak: 'break-all' }}>{event.id}</Box> },
    { label: '이벤트 기간', value: formatPeriod(event.eventStart, event.eventEnd) },
    { label: '호스팅 기간', value: formatPeriod(event.hostingStart, event.hostingEnd) },
    {
      label: '도메인',
      value: event.domain ? (
        <Stack direction="row" alignItems="center" spacing={0.5}>
          <span>{event.domain}</span>
          <CopyButton value={event.domain} toast="도메인이 복사되었습니다." />
        </Stack>
      ) : (
        <Stack direction="row" spacing={0.75} alignItems="baseline">
          <EmptyValue />
          <Typography variant="body2" sx={{ color: text.tertiary }}>
            승인 후 발급
          </Typography>
        </Stack>
      ),
    },
  ];

  return (
    <Card sx={{ p: 3 }}>
      <Stack direction={{ xs: 'column-reverse', md: 'row' }} alignItems={{ md: 'flex-start' }} justifyContent="space-between" spacing={1.5}>
        <Stack direction="row" alignItems="center" flexWrap="wrap" useFlexGap spacing={1.5}>
          <Typography variant="h2" component="h2">
            {event.name}
          </Typography>
          <StatusChip label={status.label} tone={status.tone} en={status.en} />
        </Stack>
        <Link
          component={RouterLink}
          to="/events"
          underline="none"
          sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5, flexShrink: 0, ...type.caption, color: text.secondary, '&:hover': { color: text.primary } }}
        >
          <ArrowBackRoundedIcon sx={{ fontSize: size.iconSm }} />
          이벤트 내역 목록으로 돌아가기
        </Link>
      </Stack>

      <Box sx={{ mt: 2.5, display: 'grid', gap: 2.5, gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))', lg: 'repeat(4, minmax(0, 1fr))' } }}>
        {facts.map((f) => (
          <Box key={f.label} sx={{ minWidth: 0 }}>
            <Typography variant="body2" sx={{ color: text.tertiary }}>
              {f.label}
            </Typography>
            <Box sx={{ mt: 0.25, ...type.body }}>{f.value}</Box>
          </Box>
        ))}
      </Box>
    </Card>
  );
}
