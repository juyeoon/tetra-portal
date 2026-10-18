import type { ReactNode } from 'react';
import { Typography, type SxProps, type Theme } from '@mui/material';
import { kpi } from '@/theme/theme';

interface HighlightValueProps {
  children: ReactNode;
  /** h2(KPI 값) 또는 h1(대표 숫자) */
  variant?: 'h1' | 'h2';
  sx?: SxProps<Theme>;
}

/** 강조 숫자 — Canva 표지처럼 네이비 띠 + 흰 글자. 스타일은 tokens.css의 --kpi-* 변수 */
export default function HighlightValue({ children, variant = 'h2', sx }: HighlightValueProps) {
  return (
    <Typography
      variant={variant}
      component="p"
      sx={{
        display: 'inline-block',
        px: kpi.valuePadX,
        bgcolor: kpi.valueBg,
        color: kpi.valueFg,
        borderRadius: kpi.valueRadius,
        boxDecorationBreak: 'clone',
        WebkitBoxDecorationBreak: 'clone',
        ...sx,
      }}
    >
      {children}
    </Typography>
  );
}
