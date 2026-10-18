import type { ReactNode } from 'react';
import { Box, Typography } from '@mui/material';
import { font, tones } from '@/theme/theme';

interface FormFieldProps {
  label: string;
  htmlFor: string;
  required?: boolean;
  children: ReactNode;
}

/** 입력 라벨 — 본문 크기 · medium, 필수는 * */
export default function FormField({ label, htmlFor, required, children }: FormFieldProps) {
  return (
    <Box>
      <Typography component="label" htmlFor={htmlFor} variant="body1" sx={{ display: 'block', mb: 1, fontWeight: font.weight.medium }}>
        {label}
        {required && (
          <Box component="span" sx={{ color: tones.red.dot, ml: 0.5 }}>
            *
          </Box>
        )}
      </Typography>
      {children}
    </Box>
  );
}
