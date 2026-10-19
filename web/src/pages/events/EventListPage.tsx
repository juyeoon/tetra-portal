import { useMemo, useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { Box, Button, Link, TableBody, TableCell, TableHead, TableRow, TableSortLabel, Typography } from '@mui/material';
import ChevronRightRoundedIcon from '@mui/icons-material/ChevronRightRounded';
import EventNoteOutlinedIcon from '@mui/icons-material/EventNoteOutlined';
import { listEvents } from '@/api/portal';
import { useApi } from '@/api/useApi';
import { ErrorView, LoadingView } from '@/components/common/LoadState';
import StateMessage from '@/components/common/StateMessage';
import StatusChip, { EmptyValue } from '@/components/common/StatusChip';
import TableCard from '@/components/common/TableCard';
import PageHeader from '@/components/layout/PageHeader';
import { font, size, text, type } from '@/theme/theme';
import type { EventSummary } from '@/types';
import { formatDate, formatDateRange, formatDateTime } from '@/utils/format';
import { applicationStatusMeta, eventStatusMeta, hostingStatusMeta } from '@/utils/status';

type SortKey = 'period' | 'appliedAt';
const sortLabel: Record<SortKey, string> = { period: '이벤트 기간', appliedAt: '신청일' };
// ISO +09:00 문자열이라 글자 비교 = 시각 비교
const sortValue: Record<SortKey, (e: EventSummary) => string> = {
  period: (e) => e.startAt,
  appliedAt: (e) => e.appliedAt,
};

/** UI-TP-05 이벤트 내역 - 목록 (로그인 후 홈) */
export default function EventListPage() {
  const navigate = useNavigate();
  const { data: events = [], error, loading, reload } = useApi(listEvents, []);
  const [sort, setSort] = useState<{ key: SortKey; dir: 'asc' | 'desc' }>({ key: 'period', dir: 'desc' });

  const rows = useMemo(() => {
    const get = sortValue[sort.key];
    const sorted = [...events].sort((a, b) => get(a).localeCompare(get(b)));
    return sort.dir === 'asc' ? sorted : sorted.reverse();
  }, [events, sort]);

  // 같은 헤더를 다시 누르면 오름차순/내림차순 전환
  const toggleSort = (key: SortKey) => setSort((prev) => (prev.key === key ? { key, dir: prev.dir === 'asc' ? 'desc' : 'asc' } : { key, dir: 'desc' }));

  const sortHeader = (key: SortKey) => (
    <TableSortLabel active={sort.key === key} direction={sort.key === key ? sort.dir : 'desc'} onClick={() => toggleSort(key)}>
      {sortLabel[key]}
    </TableSortLabel>
  );

  return (
    <>
      <PageHeader eyebrow="EVENTS" breadcrumbs={['이벤트 내역']} />

      {loading && events.length === 0 ? (
        <LoadingView />
      ) : error ? (
        <ErrorView message={error.message} onRetry={reload} />
      ) : events.length === 0 ? (
        <StateMessage icon={<EventNoteOutlinedIcon />} title="신청한 이벤트가 없습니다" description="이벤트를 신청하면 이곳에서 진행 상태를 확인할 수 있습니다.">
          <Button variant="contained" onClick={() => navigate('/events/new')} sx={{ mt: 2 }}>
            이벤트 신청
          </Button>
        </StateMessage>
      ) : (
        <>
          <Typography variant="body2" sx={{ mb: 1, color: text.tertiary }}>
            * 정렬 라벨을 클릭하면 오름차순/내림차순 정렬이 전환됩니다 (현재: {sortLabel[sort.key]} {sort.dir === 'desc' ? '↓ 최신순' : '↑ 오래된순'})
          </Typography>
          <TableCard minWidth={960}>
            <TableHead>
              <TableRow>
                <TableCell>이벤트명</TableCell>
                <TableCell sortDirection={sort.key === 'period' ? sort.dir : false}>{sortHeader('period')}</TableCell>
                <TableCell sortDirection={sort.key === 'appliedAt' ? sort.dir : false}>{sortHeader('appliedAt')}</TableCell>
                <TableCell>신청상태</TableCell>
                <TableCell>호스팅상태</TableCell>
                <TableCell>이벤트상태</TableCell>
                <TableCell width={40} />
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((e) => {
                const app = applicationStatusMeta[e.applicationStatus];
                const hosting = e.hostingStatus && hostingStatusMeta[e.hostingStatus];
                const status = e.eventStatus && eventStatusMeta[e.eventStatus];
                return (
                  <TableRow key={e.eventId} hover onClick={() => navigate(`/events/${e.eventId}`)} sx={{ cursor: 'pointer' }}>
                    <TableCell>
                      <Link
                        component={RouterLink}
                        to={`/events/${e.eventId}`}
                        underline="none"
                        onClick={(ev) => ev.stopPropagation()}
                        sx={{ display: 'block', fontWeight: font.weight.semibold, color: text.primary }}
                      >
                        {e.name}
                      </Link>
                      <Typography component="span" sx={{ ...type.monoSm, color: text.tertiary }}>
                        ID {e.eventId}
                      </Typography>
                    </TableCell>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDateRange(formatDateTime(e.startAt), formatDateTime(e.endAt))}</TableCell>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDate(e.appliedAt)}</TableCell>
                    <TableCell>
                      <StatusChip label={app.label} tone={app.tone} />
                    </TableCell>
                    <TableCell>{hosting ? <StatusChip label={hosting.label} tone={hosting.tone} /> : <EmptyValue />}</TableCell>
                    <TableCell>{status ? <StatusChip label={status.label} tone={status.tone} /> : <EmptyValue />}</TableCell>
                    <TableCell align="right">
                      <ChevronRightRoundedIcon sx={{ fontSize: size.iconMd, color: text.disabled, verticalAlign: 'middle' }} />
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </TableCard>
          <Box sx={{ mt: 2 }}>
            <Typography variant="body2" sx={{ color: text.tertiary }}>
              ※ 승인 전(신청됨·승인 대기·신청 실패·반려됨)에는 호스팅상태와 이벤트상태가 "-"로 표시됩니다.
            </Typography>
            <Typography variant="body2" sx={{ color: text.tertiary }}>
              ※ 행을 클릭하면 상세 페이지로 이동합니다.
            </Typography>
          </Box>
        </>
      )}
    </>
  );
}
