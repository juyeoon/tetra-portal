import { useMemo, useState, type MouseEvent, type ReactNode } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { Box, ButtonBase, Card, InputAdornment, Stack, TextField, Typography } from '@mui/material';
import NotificationsOffOutlinedIcon from '@mui/icons-material/NotificationsOffOutlined';
import SearchRoundedIcon from '@mui/icons-material/SearchRounded';
import SectionTitle from '@/components/common/SectionTitle';
import StateMessage from '@/components/common/StateMessage';
import StatusChip from '@/components/common/StatusChip';
import { color, font, line, onNavy, radius, size, text, tones, type } from '@/theme/theme';
import type { NotificationPriority, PortalNotification } from '@/types';
import { formatDate, formatTime } from '@/utils/format';
import { priorityMeta } from '@/utils/status';
import {
  CHIP_LABEL,
  NOTIFICATION_CHIPS,
  NOTIFICATION_FILTERS,
  countByChip,
  countByFilter,
  itemHref,
  pressItem,
  visibleNotifications,
  type ChipFilter,
  type NotificationFilter,
} from './notificationView';

const PRIORITIES: NotificationPriority[] = ['URGENT', 'WARNING', 'INFO'];

interface Props {
  notifications: PortalNotification[];
  /** Slack 연동 제공 여부 — MVP 1차는 false("준비 중") */
  slackAvailable: boolean;
  /** 읽음 요청(결과를 기다리지 않음 — 실패하면 페이지가 되돌리고 토스트) */
  onRead: (id: number) => void;
  /** "설정 변경" 링크 (포털: 설정 화면) */
  settingsAction?: ReactNode;
}

/** 탭 · 칩 버튼 하나(선택이면 남색) */
function FilterButton({ selected, count, onClick, children, ...aria }: {
  selected: boolean;
  count: number;
  onClick: () => void;
  children: ReactNode;
  role?: string;
  'aria-selected'?: boolean;
  'aria-pressed'?: boolean;
}) {
  return (
    <ButtonBase
      {...aria}
      onClick={onClick}
      sx={{
        gap: 0.75,
        height: size.controlSm,
        px: 1.5,
        bgcolor: selected ? color.navy : 'transparent',
        color: selected ? color.white : text.secondary,
        border: `1px solid ${selected ? color.navy : line.base}`,
        ...type.caption,
        fontWeight: selected ? font.weight.semibold : font.weight.medium,
        '&:hover': { borderColor: selected ? color.navy : line.strong },
      }}
    >
      {children}
      <Box component="span" sx={{ minWidth: 20, px: 0.5, bgcolor: selected ? onNavy.active : color.card, fontWeight: font.weight.bold }}>
        {count}
      </Box>
    </ButtonBase>
  );
}

const ROW_SX = {
  width: '100%',
  justifyContent: 'flex-start',
  textAlign: 'left',
  gap: 1.5,
  px: 1.5,
  py: 1.75,
  color: 'inherit',
  '&:hover': { bgcolor: color.card },
} as const;

/** 알림 한 줄 — [우선순위] [칩] 내용 · 오른쪽 시각. 경로가 있으면 링크(가운데 클릭 · 새 탭 가능), 없으면 읽음만 */
function NotificationRow({ n, onRead }: { n: PortalNotification; onRead: (id: number) => void }) {
  const meta = priorityMeta[n.priority];
  const href = itemHref(n);
  const press = (e: MouseEvent) => pressItem(n, e, onRead);

  const body = (
    <>
      <Box aria-label={n.read ? undefined : '안읽음'} sx={{ width: 7, height: 7, flexShrink: 0, bgcolor: n.read ? 'transparent' : tones.blue.dot }} />
      <StatusChip label={meta.label} tone={meta.tone} />
      {n.chip && <StatusChip label={CHIP_LABEL[n.chip]} tone="grey" />}
      <Typography variant="body1" sx={{ flex: 1, minWidth: 0, fontWeight: n.read ? font.weight.regular : font.weight.semibold, color: n.read ? text.secondary : text.primary }}>
        {n.content}
      </Typography>
      <Box sx={{ flexShrink: 0, textAlign: 'right' }}>
        <Typography variant="body2" sx={{ color: text.tertiary }}>
          {formatDate(n.occurredAt)}
        </Typography>
        <Typography variant="body2" sx={{ color: text.tertiary }}>
          {formatTime(n.occurredAt)}
        </Typography>
      </Box>
    </>
  );

  return href ? (
    <ButtonBase component={RouterLink} to={href} onClick={press} onAuxClick={press} sx={ROW_SX}>
      {body}
    </ButtonBase>
  ) : (
    <ButtonBase onClick={press} onAuxClick={press} sx={ROW_SX}>
      {body}
    </ButtonBase>
  );
}

/**
 * 알림 센터 (UI-TP-13) — Slack 우선순위별 채널 · 우선순위 탭 5종(건수) · 칩(종류, 지금 탭 기준 건수) · 검색 · 알림 리스트(최신순).
 * 검색은 입력 즉시 반영. 항목을 누르면 읽음 + 관련 화면으로 이동(경로가 없으면 읽음만).
 */
export default function NotificationCenter({ notifications, slackAvailable, onRead, settingsAction }: Props) {
  const [filter, setFilter] = useState<NotificationFilter>('ALL');
  const [chip, setChip] = useState<ChipFilter>('ALL');
  const [query, setQuery] = useState('');

  const counts = useMemo(() => countByFilter(notifications), [notifications]);
  const chipCounts = useMemo(() => countByChip(notifications, filter), [notifications, filter]);
  const visible = useMemo(() => visibleNotifications(notifications, filter, chip, query), [notifications, filter, chip, query]);

  return (
    <Stack spacing={3}>
      {/* ①② Slack 연동 · 우선순위별 채널 */}
      <Card sx={{ p: 3 }}>
        <SectionTitle
          action={
            <Stack direction="row" alignItems="center" spacing={1.5}>
              <StatusChip label={slackAvailable ? 'Slack 미연동' : 'Slack 준비 중'} tone="grey" />
              {settingsAction}
            </Stack>
          }
        >
          Slack 연동 · 우선순위별 채널
        </SectionTitle>
        <Box sx={{ display: 'grid', gap: 1.5, gridTemplateColumns: { xs: '1fr', md: 'repeat(3, 1fr)' } }}>
          {PRIORITIES.map((p) => {
            const meta = priorityMeta[p];
            return (
              <Box key={p} sx={{ p: 2, borderRadius: radius.lg, bgcolor: color.surface, border: `1px solid ${line.soft}` }}>
                <StatusChip label={meta.label} en={meta.en} tone={meta.tone} />
                <Typography sx={{ mt: 1, ...type.monoSm, color: text.tertiary, wordBreak: 'break-all' }}>
                  {slackAvailable ? '채널 미등록' : '준비 중'}
                </Typography>
              </Box>
            );
          })}
        </Box>
        {!slackAvailable && (
          <Typography variant="body2" sx={{ mt: 1.5, color: text.tertiary }}>
            ※ Slack 연동은 준비 중입니다. 알림은 이 화면과, 설정에서 켠 경우 로그인 계정 이메일로 받습니다.
          </Typography>
        )}
      </Card>

      <Box>
        {/* ③ 우선순위 탭 · ④ 검색 */}
        <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" alignItems={{ md: 'center' }} spacing={1.5} sx={{ mb: 1 }}>
          <Stack role="tablist" aria-label="알림 우선순위" direction="row" flexWrap="wrap" useFlexGap spacing={0.75}>
            {NOTIFICATION_FILTERS.map((f) => (
              <FilterButton key={f.key} role="tab" aria-selected={f.key === filter} selected={f.key === filter} count={counts[f.key]} onClick={() => setFilter(f.key)}>
                {f.label}
              </FilterButton>
            ))}
          </Stack>
          <TextField
            size="small"
            placeholder="알림 내용 검색"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            sx={{ width: { xs: '100%', md: 280 } }}
            slotProps={{
              htmlInput: { 'aria-label': '알림 내용 검색' },
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchRoundedIcon sx={{ fontSize: size.iconMd, color: text.tertiary }} />
                  </InputAdornment>
                ),
              },
            }}
          />
        </Stack>

        {/* 칩(종류) — 탭과 함께 거르고, 건수는 지금 탭 기준 */}
        <Stack role="group" aria-label="알림 종류" direction="row" flexWrap="wrap" useFlexGap spacing={0.75} sx={{ mb: 1.5 }}>
          {NOTIFICATION_CHIPS.map((c) => (
            <FilterButton key={c.key} aria-pressed={c.key === chip} selected={c.key === chip} count={chipCounts[c.key]} onClick={() => setChip(c.key)}>
              {c.label}
            </FilterButton>
          ))}
        </Stack>

        {/* ⑤ 알림 리스트 */}
        {visible.length === 0 ? (
          <StateMessage icon={<NotificationsOffOutlinedIcon />} tone="grey" title={query.trim() ? '검색 결과가 없습니다' : '알림이 없습니다'} />
        ) : (
          <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, borderTop: `1px solid ${line.strong}` }}>
            {visible.map((n) => (
              <Box component="li" key={n.notificationId} sx={{ borderBottom: `1px solid ${line.base}` }}>
                <NotificationRow n={n} onRead={onRead} />
              </Box>
            ))}
          </Box>
        )}
      </Box>
    </Stack>
  );
}
