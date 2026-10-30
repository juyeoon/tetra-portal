import { useId, useMemo, useState, type MouseEvent } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import {
  Box,
  Button,
  ButtonBase,
  Checkbox,
  FormControlLabel,
  InputAdornment,
  Menu,
  MenuItem,
  Stack,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material';
import KeyboardArrowDownRoundedIcon from '@mui/icons-material/KeyboardArrowDownRounded';
import NotificationsOffOutlinedIcon from '@mui/icons-material/NotificationsOffOutlined';
import SearchRoundedIcon from '@mui/icons-material/SearchRounded';
import StateMessage from '@/components/common/StateMessage';
import StatusChip from '@/components/common/StatusChip';
import { color, font, ink, line, onNavy, shadow, size, text, tones, type } from '@/theme/theme';
import type { PortalNotification } from '@/types';
import { formatDate, formatTime } from '@/utils/format';
import { priorityMeta } from '@/utils/status';
import {
  CHIP_LABEL,
  PRIORITY_FILTERS,
  chipButtonLabel,
  chipMenuItems,
  countByPriority,
  itemHref,
  pressItem,
  visibleNotifications,
  type ChipFilter,
  type ChipMenuItem,
  type PriorityFilter,
} from './notificationView';

interface Props {
  notifications: PortalNotification[];
  /** 읽음 요청(결과를 기다리지 않음 — 실패하면 페이지가 되돌리고 토스트) */
  onRead: (id: number) => void;
}

/** 우선순위 세그먼트 한 칸 — 선택이면 남색 */
const SEGMENT_SX = {
  gap: 0.75,
  height: size.controlSm,
  px: { xs: 1, sm: 1.5 },
  py: 0,
  border: `1px solid ${line.base}`,
  color: text.secondary,
  ...type.caption,
  fontWeight: font.weight.medium,
  whiteSpace: 'nowrap',
  '&:hover': { bgcolor: ink[5] },
  '&.Mui-selected, &.Mui-selected:hover': { bgcolor: color.navy, borderColor: color.navy, color: color.white, fontWeight: font.weight.semibold },
  '&.Mui-focusVisible': { boxShadow: shadow.focus, zIndex: 1 },
} as const;

/** 분류 드롭다운 — '분류: 전체 ▾'. 전체가 아니면 테두리 · 글자를 포인트 색으로. 메뉴는 이름 + 건수, 0건은 흐리게(고를 수는 있다) */
function ChipMenu({ value, items, onChange }: { value: ChipFilter; items: ChipMenuItem[]; onChange: (chip: ChipFilter) => void }) {
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const menuId = useId();
  const active = value !== 'ALL';

  return (
    <>
      <Button
        variant="outlined"
        size="small"
        aria-haspopup="true"
        aria-expanded={!!anchor}
        aria-controls={anchor ? menuId : undefined}
        endIcon={<KeyboardArrowDownRoundedIcon />}
        onClick={(e) => setAnchor(e.currentTarget)}
        sx={{
          whiteSpace: 'nowrap',
          ...(active && { borderColor: color.point, color: color.pointDeep, '&:hover': { borderColor: color.point, bgcolor: color.pointTint } }),
        }}
      >
        {chipButtonLabel(value)}
      </Button>
      <Menu
        id={menuId}
        anchorEl={anchor}
        open={!!anchor}
        onClose={() => setAnchor(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
        transformOrigin={{ vertical: 'top', horizontal: 'left' }}
        slotProps={{ paper: { sx: { mt: 0.5, minWidth: 180 } } }}
      >
        {items.map((c) => (
          <MenuItem
            key={c.key}
            role="menuitemradio"
            aria-checked={c.key === value}
            selected={c.key === value}
            onClick={() => {
              setAnchor(null);
              onChange(c.key);
            }}
            sx={{ justifyContent: 'space-between', gap: 3, color: c.dim ? text.tertiary : text.primary }}
          >
            {c.label}
            <Box component="span" sx={{ ...type.caption, fontWeight: font.weight.semibold, color: c.dim ? text.disabled : text.tertiary }}>
              {c.count}
            </Box>
          </MenuItem>
        ))}
      </Menu>
    </>
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
 * 알림 센터 (UI-TP-13) — 필터 한 줄(우선순위 세그먼트 · 분류 드롭다운 · 안 읽은 것만 · 검색) + 알림 리스트(최신순).
 * 검색은 입력 즉시 반영. 항목을 누르면 읽음 + 관련 화면으로 이동(경로가 없으면 읽음만). 알림 채널(Slack) 설정은 설정 화면에서 한다.
 */
export default function NotificationCenter({ notifications, onRead }: Props) {
  const [priority, setPriority] = useState<PriorityFilter>('ALL');
  const [chip, setChip] = useState<ChipFilter>('ALL');
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [query, setQuery] = useState('');

  const counts = useMemo(() => countByPriority(notifications, { unreadOnly }), [notifications, unreadOnly]);
  const chipItems = useMemo(() => chipMenuItems(notifications, { priority, unreadOnly }), [notifications, priority, unreadOnly]);
  const visible = useMemo(
    () => visibleNotifications(notifications, { priority, chip, unreadOnly, query }),
    [notifications, priority, chip, unreadOnly, query],
  );

  return (
    <Box>
      {/* ① 필터 한 줄 — 왼쪽 우선순위, 오른쪽 분류 · 안 읽은 것만 · 검색(좁으면 오른쪽 묶음이 다음 줄로) */}
      <Stack direction="row" flexWrap="wrap" useFlexGap justifyContent="space-between" alignItems="center" spacing={1.5} sx={{ mb: 1.5 }}>
        <ToggleButtonGroup
          exclusive
          size="small"
          value={priority}
          onChange={(_, next: PriorityFilter | null) => {
            if (next) setPriority(next);
          }}
          aria-label="알림 우선순위"
        >
          {PRIORITY_FILTERS.map((p) => (
            <ToggleButton key={p.key} value={p.key} sx={SEGMENT_SX}>
              {p.label}
              <Box component="span" sx={{ minWidth: 20, px: 0.5, bgcolor: p.key === priority ? onNavy.active : color.card, fontWeight: font.weight.bold }}>
                {counts[p.key]}
              </Box>
            </ToggleButton>
          ))}
        </ToggleButtonGroup>

        <Stack direction="row" flexWrap="wrap" useFlexGap alignItems="center" spacing={1.5} sx={{ width: { xs: '100%', sm: 'auto' } }}>
          <ChipMenu value={chip} items={chipItems} onChange={setChip} />
          <FormControlLabel
            label="안 읽은 것만"
            control={
              <Checkbox
                size="small"
                checked={unreadOnly}
                onChange={(e) => setUnreadOnly(e.target.checked)}
                sx={{ '&.Mui-focusVisible': { boxShadow: shadow.focus } }}
              />
            }
            slotProps={{ typography: { variant: 'body2', sx: { color: text.secondary, whiteSpace: 'nowrap' } } }}
            sx={{ m: 0 }}
          />
          <TextField
            size="small"
            placeholder="알림 내용 검색"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            sx={{
              width: { xs: '100%', sm: 240 },
              // 한 줄의 다른 칸(세그먼트 · 분류 버튼)과 같은 높이
              '& .MuiOutlinedInput-input': { paddingBlock: `calc((${size.controlSm} - ${font.lineHeight.body}) / 2)` },
            }}
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
      </Stack>

      {/* ② 알림 리스트 */}
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
  );
}
