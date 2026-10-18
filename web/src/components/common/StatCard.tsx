import type { ReactNode } from 'react';
import { Box, Typography } from '@mui/material';
import { color, font, kpi, radius, text } from '@/theme/theme';
import HighlightValue from './HighlightValue';

const fills = { peri: color.periTint, blue: color.blueTint, card: color.card };

interface StatCardProps {
  label: ReactNode;
  value: ReactNode;
  sub?: ReactNode;
  /** 배경 톤 — Snow처럼 KPI 카드는 파스텔 배경을 번갈아 사용 */
  fill?: keyof typeof fills;
  children?: ReactNode;
}

/**
 * KPI 카드 — 제목은 카드 제목(h4)과 같은 스타일, 값은 Canva 표지처럼 네이비 띠 + 흰 글자로 강조.
 * 강조 스타일은 tokens.css의 --kpi-* 변수로 조정한다.
 */
export default function StatCard({ label, value, sub, fill = 'card', children }: StatCardProps) {
  return (
    <Box sx={{ p: 3, height: '100%', borderRadius: radius.xl, bgcolor: fills[fill], containerType: 'inline-size' }}>
      <Typography variant="h4" component="h3" sx={{ color: kpi.labelColor }}>
        {label}
      </Typography>
      <Box sx={{ mt: 1.5 }}>
        <HighlightValue sx={{ whiteSpace: 'nowrap', fontSize: `min(${font.size.page}, ${kpi.valueFluid})` }}>{value}</HighlightValue>
      </Box>
      {sub && (
        <Typography variant="body2" sx={{ mt: 1, color: text.tertiary }}>
          {sub}
        </Typography>
      )}
      {children}
    </Box>
  );
}
