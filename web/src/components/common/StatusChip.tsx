import { Box } from '@mui/material';
import { font, radius, size, text, tones, type, type Tone } from '@/theme/theme';

interface StatusChipProps {
  label: string;
  tone: Tone;
  /** 영문 병기 (예: 검증됨 · Verified) */
  en?: string;
}

/** 상태 배지 — 점 없는 사각 배지 */
export default function StatusChip({ label, tone, en }: StatusChipProps) {
  const t = tones[tone];
  return (
    <Box
      component="span"
      sx={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 0.5,
        height: size.badge,
        px: 1.25,
        borderRadius: radius.sm,
        bgcolor: t.bg,
        color: t.fg,
        ...type.caption,
        fontWeight: font.weight.semibold,
        whiteSpace: 'nowrap',
      }}
    >
      {label}
      {en && (
        <Box component="span" sx={{ fontWeight: font.weight.regular, opacity: 0.8 }}>
          · {en}
        </Box>
      )}
    </Box>
  );
}

/** 값이 없을 때 표시 */
export function EmptyValue() {
  return (
    <Box component="span" sx={{ color: text.tertiary }}>
      -
    </Box>
  );
}
