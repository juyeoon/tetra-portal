import { Box, Stack, Typography } from '@mui/material';
import ImageOutlinedIcon from '@mui/icons-material/ImageOutlined';
import { color, font, ink, line, previewThemes, radius, size, type } from '@/theme/theme';
import type { HAlign, ScreenSetting, ScreenTheme, ScreenWidget, VAlign } from '@/types';

export const WIDGET_ORDER: ScreenWidget[] = ['title', 'banner', 'couponList', 'issueButton'];
export const widgetLabel: Record<ScreenWidget, string> = { title: '제목', banner: '배너', couponList: '쿠폰 목록(카드)', issueButton: '발급 버튼' };
export const hLabel: Record<HAlign, string> = { LEFT: '좌', CENTER: '중', RIGHT: '우' };
export const vLabel: Record<VAlign, string> = { TOP: '상', MIDDLE: '중', BOTTOM: '하' };
export const themeLabel: Record<ScreenTheme, string> = { WHITE: '화이트', WARM_ORANGE: '웜 오렌지', DARK: '다크', BLUE: '블루' };

const justify: Record<HAlign, string> = { LEFT: 'flex-start', CENTER: 'center', RIGHT: 'flex-end' };

/** 선택 요약 — "제목 상-중 / 배너 상-중 / … · 테마 화이트" */
export const screenSummary = (s: ScreenSetting) =>
  `${WIDGET_ORDER.map((w) => `${widgetLabel[w].replace('(카드)', '')} ${vLabel[s[w].v]}-${hLabel[s[w].h]}`).join(' / ')} · 테마 ${themeLabel[s.theme]}`;

function Widget({ widget, accent, fg }: { widget: ScreenWidget; accent: string; fg: string }) {
  switch (widget) {
    case 'title':
      return (
        <Typography sx={{ ...type.caption, fontWeight: font.weight.bold, color: fg, px: 1.5, py: 0.25, borderRadius: radius.sm, bgcolor: ink[5] }}>
          이벤트 제목
        </Typography>
      );
    case 'banner':
      return (
        <Box sx={{ width: '44%', minWidth: 120, aspectRatio: '1200 / 420', display: 'grid', placeItems: 'center', borderRadius: radius.sm, border: '1px dashed', borderColor: fg, color: fg, opacity: 0.45 }}>
          <ImageOutlinedIcon sx={{ fontSize: size.iconMd }} />
        </Box>
      );
    case 'couponList':
      return (
        <Stack direction="row" spacing={0.75}>
          {[0, 1].map((i) => (
            <Box key={i} sx={{ width: 56, height: 26, px: 0.75, borderRadius: radius.sm, border: `1px dashed ${accent}`, display: 'grid', alignContent: 'center', gap: '3px' }}>
              <Box sx={{ height: 2, width: '80%', bgcolor: fg, opacity: 0.4 }} />
              <Box sx={{ height: 2, width: '55%', bgcolor: fg, opacity: 0.25 }} />
            </Box>
          ))}
        </Stack>
      );
    case 'issueButton':
      return (
        <Box sx={{ px: 2, py: 0.5, bgcolor: accent, color: color.white, ...type.caption, fontWeight: font.weight.bold }}>
          쿠폰 받기
        </Box>
      );
  }
}

interface ScreenPreviewProps {
  screen: ScreenSetting;
  /** 브라우저 주소창에 표시할 도메인 */
  domain?: string;
  height?: number;
}

/**
 * 이벤트 화면 미리보기 (예시 그림) — 위치와 테마만 반영하고 배너 이미지·쿠폰 정보는 반영하지 않는다 (UI-TP-09 ②)
 */
export default function ScreenPreview({ screen, domain = 'event.tetra-shop.com', height = 440 }: ScreenPreviewProps) {
  const t = previewThemes[screen.theme];
  const zones: VAlign[] = ['TOP', 'MIDDLE', 'BOTTOM'];

  return (
    <Box sx={{ borderRadius: radius.lg, overflow: 'hidden', border: `1px solid ${line.base}` }}>
      {/* 브라우저 상단 바 */}
      <Stack direction="row" alignItems="center" spacing={1} sx={{ px: 1.5, py: 1, bgcolor: ink[5], borderBottom: `1px solid ${line.base}` }}>
        <Stack direction="row" spacing={0.5}>
          {[0, 1, 2].map((i) => (
            <Box key={i} sx={{ width: 7, height: 7, bgcolor: ink[20] }} />
          ))}
        </Stack>
        <Typography sx={{ ...type.caption, color: ink[40] }}>{domain}</Typography>
      </Stack>

      <Box sx={{ height, display: 'grid', gridTemplateRows: 'repeat(3, 1fr)', bgcolor: t.bg, color: t.fg, transition: 'background-color .2s' }}>
        {zones.map((zone) => (
          <Stack key={zone} justifyContent="center" spacing={1.25} sx={{ px: 3, py: 1.5 }}>
            {WIDGET_ORDER.filter((w) => screen[w].v === zone).map((w) => (
              <Box key={w} sx={{ display: 'flex', justifyContent: justify[screen[w].h] }}>
                <Widget widget={w} accent={t.accent} fg={t.fg} />
              </Box>
            ))}
          </Stack>
        ))}
      </Box>
    </Box>
  );
}
