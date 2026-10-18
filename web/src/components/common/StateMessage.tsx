import type { ReactNode } from 'react';
import { Box, Stack, Typography } from '@mui/material';
import { radius, size, text, tones, type Tone } from '@/theme/theme';

interface StateMessageProps {
  icon: ReactNode;
  tone?: Tone;
  title: string;
  description?: ReactNode;
  children?: ReactNode;
}

/** 결과 대기/빈 상태 안내 */
export default function StateMessage({ icon, tone = 'blue', title, description, children }: StateMessageProps) {
  const t = tones[tone];
  return (
    <Stack alignItems="center" spacing={1} sx={{ py: { xs: 8, md: 12 }, textAlign: 'center' }}>
      <Box sx={{ width: size.iconBoxLg, height: size.iconBoxLg, mb: 1, borderRadius: radius.lg, display: 'grid', placeItems: 'center', bgcolor: t.bg, color: t.fg, '& svg': { fontSize: size.iconLg } }}>
        {icon}
      </Box>
      <Typography variant="h3">{title}</Typography>
      {description && (
        <Typography variant="body1" sx={{ color: text.secondary }}>
          {description}
        </Typography>
      )}
      {children}
    </Stack>
  );
}
