import { Fragment } from 'react';
import { Box, Stack, Typography } from '@mui/material';
import CheckRoundedIcon from '@mui/icons-material/CheckRounded';
import { color, font, ink, line, text, type } from '@/theme/theme';

export const STEPS = ['이벤트 타입', '이벤트 정보', '이벤트 화면 설정', '신청서 제출'] as const;

/** 상단 진행 단계 — 현재 위치만 표시 (클릭 이동 없음) */
export default function StepProgress({ current }: { current: number }) {
  return (
    <Stack component="ol" direction="row" alignItems="flex-start" sx={{ listStyle: 'none', m: 0, p: 0, mb: 4, maxWidth: 720 }} aria-label="신청 진행 단계">
      {STEPS.map((label, i) => {
        const done = i < current;
        const active = i === current;
        return (
          <Fragment key={label}>
            {i > 0 && <Box aria-hidden sx={{ flex: 1, height: 2, mt: '13px', mx: 1, bgcolor: i <= current ? color.navy : line.base }} />}
            <Stack component="li" alignItems="center" spacing={0.75} aria-current={active ? 'step' : undefined} sx={{ width: 96, flexShrink: 0 }}>
              <Box
                sx={{
                  width: 28,
                  height: 28,
                  display: 'grid',
                  placeItems: 'center',
                  ...type.caption,
                  fontWeight: font.weight.bold,
                  bgcolor: active ? color.point : done ? color.navy : ink[5],
                  color: active ? color.onPoint : done ? color.white : text.tertiary,
                  boxShadow: active ? `0 0 0 4px ${color.pointTint}` : 'none',
                }}
              >
                {done ? <CheckRoundedIcon sx={{ fontSize: 16 }} /> : i + 1}
              </Box>
              <Typography
                variant="body2"
                sx={{ textAlign: 'center', whiteSpace: 'nowrap', color: active ? color.pointDeep : done ? text.primary : text.tertiary, fontWeight: active ? font.weight.semibold : font.weight.medium }}
              >
                {label}
              </Typography>
            </Stack>
          </Fragment>
        );
      })}
    </Stack>
  );
}
