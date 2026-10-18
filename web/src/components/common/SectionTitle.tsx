import type { ReactNode } from 'react';
import { Stack, Typography } from '@mui/material';
import { size } from '@/theme/theme';

interface SectionTitleProps {
  children: ReactNode;
  action?: ReactNode;
  mb?: number;
}

/** 카드 제목 — h4 (제목체 · 본문 크기 · 600) */
export default function SectionTitle({ children, action, mb = 2 }: SectionTitleProps) {
  return (
    <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={2} sx={{ mb, minHeight: size.controlSm }}>
      <Typography variant="h4" component="h3">
        {children}
      </Typography>
      {action}
    </Stack>
  );
}
