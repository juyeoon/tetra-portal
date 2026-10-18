import { useMemo, useState, type ReactNode } from 'react';
import { Box, ButtonBase, Card, InputAdornment, Stack, TextField, Typography } from '@mui/material';
import NotificationsOffOutlinedIcon from '@mui/icons-material/NotificationsOffOutlined';
import SearchRoundedIcon from '@mui/icons-material/SearchRounded';
import SectionTitle from '@/components/common/SectionTitle';
import StateMessage from '@/components/common/StateMessage';
import StatusChip from '@/components/common/StatusChip';
import { color, font, line, onNavy, radius, size, text, tones, type } from '@/theme/theme';
import type { NotificationPriority, PortalNotification, SlackSetting } from '@/types';
import { priorityMeta } from '@/utils/status';

type Filter = 'ALL' | NotificationPriority | 'UNREAD';
const FILTERS: { key: Filter; label: string }[] = [
  { key: 'ALL', label: '전체보기' },
  { key: 'CRITICAL', label: '긴급' },
  { key: 'WARNING', label: '경고' },
  { key: 'INFO', label: '정보' },
  { key: 'UNREAD', label: '안읽음' },
];
const PRIORITIES: NotificationPriority[] = ['CRITICAL', 'WARNING', 'INFO'];

const matches = (n: PortalNotification, f: Filter) => (f === 'ALL' ? true : f === 'UNREAD' ? !n.read : n.priority === f);

interface Props {
  notifications: PortalNotification[];
  slack: SlackSetting;
  onRead: (id: number) => void;
  /** "설정 변경" 링크 (포털: 설정 화면) */
  settingsAction?: ReactNode;
}

/**
 * 알림 센터 (UI-TP-13) — Slack 우선순위별 채널 · 필터 탭 5종 · 검색 · 알림 리스트(최신순).
 * 검색은 입력 즉시 반영, 항목을 클릭하면 읽음 처리한다. 콘솔 알림센터에서도 재사용할 수 있다.
 */
export default function NotificationCenter({ notifications, slack, onRead, settingsAction }: Props) {
  const [filter, setFilter] = useState<Filter>('ALL');
  const [query, setQuery] = useState('');

  const sorted = useMemo(() => [...notifications].sort((a, b) => b.at.localeCompare(a.at)), [notifications]);
  const visible = sorted.filter((n) => matches(n, filter) && n.message.toLowerCase().includes(query.trim().toLowerCase()));

  return (
    <Stack spacing={3}>
      {/* ①② Slack 연동 · 우선순위별 채널 */}
      <Card sx={{ p: 3 }}>
        <SectionTitle
          action={
            <Stack direction="row" alignItems="center" spacing={1.5}>
              {slack.connected ? <StatusChip label="Slack 연동됨" en="Connected" tone="green" /> : <StatusChip label="Slack 미연동" tone="grey" />}
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
                <Typography sx={{ mt: 1, ...type.monoSm, color: slack.connected && slack.channels[p] ? text.primary : text.tertiary, wordBreak: 'break-all' }}>
                  {slack.connected && slack.channels[p] ? slack.channels[p] : '채널 미등록'}
                </Typography>
              </Box>
            );
          })}
        </Box>
      </Card>

      <Box>
        {/* ③ 필터 탭 · ④ 검색 */}
        <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" alignItems={{ md: 'center' }} spacing={1.5} sx={{ mb: 1.5 }}>
          <Stack role="tablist" aria-label="알림 필터" direction="row" flexWrap="wrap" useFlexGap spacing={0.75}>
            {FILTERS.map((f) => {
              const selected = f.key === filter;
              const count = notifications.filter((n) => matches(n, f.key)).length;
              return (
                <ButtonBase
                  key={f.key}
                  role="tab"
                  aria-selected={selected}
                  onClick={() => setFilter(f.key)}
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
                  {f.label}
                  <Box component="span" sx={{ minWidth: 20, px: 0.5, bgcolor: selected ? onNavy.active : color.card, fontWeight: font.weight.bold }}>
                    {count}
                  </Box>
                </ButtonBase>
              );
            })}
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

        {/* ⑤ 알림 리스트 */}
        {visible.length === 0 ? (
          <StateMessage icon={<NotificationsOffOutlinedIcon />} tone="grey" title={query ? '검색 결과가 없습니다' : '알림이 없습니다'} />
        ) : (
          <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, borderTop: `1px solid ${line.strong}` }}>
            {visible.map((n) => {
              const meta = priorityMeta[n.priority];
              return (
                <Box component="li" key={n.id} sx={{ borderBottom: `1px solid ${line.base}` }}>
                  <ButtonBase
                    onClick={() => !n.read && onRead(n.id)}
                    sx={{ width: '100%', justifyContent: 'flex-start', textAlign: 'left', gap: 1.5, px: 1.5, py: 1.75, '&:hover': { bgcolor: color.card } }}
                  >
                    <Box
                      aria-label={n.read ? undefined : '안읽음'}
                      sx={{ width: 7, height: 7, flexShrink: 0, bgcolor: n.read ? 'transparent' : tones.blue.dot }}
                    />
                    <StatusChip label={meta.label} tone={meta.tone} />
                    <Typography variant="body1" sx={{ flex: 1, minWidth: 0, fontWeight: n.read ? font.weight.regular : font.weight.semibold, color: n.read ? text.secondary : text.primary }}>
                      {n.message}
                    </Typography>
                    <Box sx={{ flexShrink: 0, textAlign: 'right' }}>
                      <Typography variant="body2" sx={{ color: text.tertiary }}>
                        {n.at.slice(0, 10)}
                      </Typography>
                      <Typography variant="body2" sx={{ color: text.tertiary }}>
                        {n.at.slice(11)}
                      </Typography>
                    </Box>
                  </ButtonBase>
                </Box>
              );
            })}
          </Box>
        )}
      </Box>
    </Stack>
  );
}
