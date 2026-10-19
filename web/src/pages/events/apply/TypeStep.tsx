import type { ReactNode } from 'react';
import { Box, Button, Card, Stack, Tooltip, Typography } from '@mui/material';
import ConfirmationNumberOutlinedIcon from '@mui/icons-material/ConfirmationNumberOutlined';
import LiveTvOutlinedIcon from '@mui/icons-material/LiveTvOutlined';
import TimerOutlinedIcon from '@mui/icons-material/TimerOutlined';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import { color, font, radius, size, text } from '@/theme/theme';
import type { EventType } from '@/types';
import { eventTypeLabel } from '@/utils/status';
import { AVAILABLE_TYPES } from './draft';

const TYPES: { type: EventType; icon: ReactNode; description: string; guide: string }[] = [
  {
    type: 'COUPON',
    icon: <ConfirmationNumberOutlinedIcon />,
    description: '할인 쿠폰, 무료배송 쿠폰 등 발급형 혜택을 제공할 때 선택하세요. 발급 수량 제한과 선착순 지급 방식에 적합합니다.',
    guide: '선착순으로 쿠폰을 발급합니다. 쿠폰 종류(최대 10종)와 전체 발급 재고를 입력합니다.',
  },
  {
    type: 'TIMESALE',
    icon: <TimerOutlinedIcon />,
    description: '한정된 시간 동안 특가로 상품을 판매할 때 선택하세요. 정가·세일가·할인율 기반의 상품 구성이 가능합니다.',
    guide: '정해진 시간에만 열리는 특가 판매 이벤트입니다.',
  },
  {
    type: 'LIVE_COMMERCE',
    icon: <LiveTvOutlinedIcon />,
    description: '실시간 방송을 통해 상품을 소개하고 판매할 때 선택하세요. 방송 일정·플랫폼·쇼호스트 정보를 함께 등록할 수 있습니다.',
    guide: '라이브 방송과 함께 진행하는 판매 이벤트입니다.',
  },
];

/** UI-TP-07 이벤트 신청 - Tab1 (이벤트 타입) */
export default function TypeStep({ onSelect }: { onSelect: (type: EventType) => void }) {
  return (
    <>
      <Typography variant="h3" component="h2">
        이벤트 타입을 선택하세요
      </Typography>
      <Typography variant="body1" sx={{ mt: 0.5, mb: 3, color: text.secondary }}>
        선택한 타입에 따라 이후 입력 항목(이벤트 정보 단계)이 자동으로 달라집니다.
      </Typography>

      <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', md: 'repeat(3, 1fr)' } }}>
        {TYPES.map((t) => {
          // 템플릿이 준비되지 않은 타입은 고를 수 없다 (서버도 거절)
          const available = AVAILABLE_TYPES.includes(t.type);
          return (
          <Card key={t.type} sx={{ p: 3, display: 'flex', flexDirection: 'column', opacity: available ? 1 : 0.6 }}>
            <Stack direction="row" alignItems="center" spacing={0.75}>
              <Typography variant="h3" component="h3">
                {eventTypeLabel[t.type]}
              </Typography>
              <Tooltip title={t.guide} arrow>
                <Box component="span" tabIndex={0} aria-label={`${eventTypeLabel[t.type]} 안내`} sx={{ display: 'inline-flex', color: text.tertiary, cursor: 'help' }}>
                  <InfoOutlinedIcon sx={{ fontSize: size.iconSm }} />
                </Box>
              </Tooltip>
            </Stack>
            <Box sx={{ my: 3, mx: 'auto', width: 72, height: 72, display: 'grid', placeItems: 'center', bgcolor: color.surface, color: color.navy, '& svg': { fontSize: 32 } }}>
              {t.icon}
            </Box>
            <Typography variant="body1" sx={{ flex: 1, color: text.secondary, minHeight: 72 }}>
              {t.description}
            </Typography>
            <Button variant="contained" size="large" fullWidth disabled={!available} onClick={() => onSelect(t.type)} sx={{ mt: 3, borderRadius: radius.md, fontWeight: font.weight.semibold }}>
              {available ? '선택' : '준비 중'}
            </Button>
          </Card>
          );
        })}
      </Box>
    </>
  );
}
