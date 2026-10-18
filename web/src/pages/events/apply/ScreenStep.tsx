import { Box, Button, ButtonBase, Card, Stack, Typography } from '@mui/material';
import ArrowBackRoundedIcon from '@mui/icons-material/ArrowBackRounded';
import SectionTitle from '@/components/common/SectionTitle';
import ScreenPreview, { WIDGET_ORDER, hLabel, screenSummary, themeLabel, vLabel, widgetLabel } from '@/components/event/ScreenPreview';
import { color, font, line, previewThemes, radius, size, text, type } from '@/theme/theme';
import type { HAlign, ScreenSetting, ScreenTheme, ScreenWidget, VAlign } from '@/types';

interface Props {
  screen: ScreenSetting;
  onChange: (screen: ScreenSetting) => void;
  onPrev: () => void;
  onNext: () => void;
}

const H: HAlign[] = ['LEFT', 'CENTER', 'RIGHT'];
const V: VAlign[] = ['TOP', 'MIDDLE', 'BOTTOM'];
const THEMES: ScreenTheme[] = ['WHITE', 'WARM_ORANGE', 'DARK', 'BLUE'];

/** 좌·중·우 / 상·중·하 선택 */
function Segmented<T extends string>({ options, value, label, onSelect, ariaLabel }: { options: T[]; value: T; label: Record<T, string>; onSelect: (v: T) => void; ariaLabel: string }) {
  return (
    <Stack role="radiogroup" aria-label={ariaLabel} direction="row" sx={{ border: `1px solid ${line.strong}`, borderRadius: radius.md, overflow: 'hidden' }}>
      {options.map((o, i) => {
        const selected = o === value;
        return (
          <ButtonBase
            key={o}
            role="radio"
            aria-checked={selected}
            onClick={() => onSelect(o)}
            sx={{
              width: 36,
              height: size.controlSm,
              borderLeft: i === 0 ? 0 : `1px solid ${line.base}`,
              bgcolor: selected ? color.pointTint : color.surface,
              color: selected ? color.pointDeep : text.secondary,
              boxShadow: selected ? `inset 0 -2px 0 ${color.point}` : 'none',
              ...type.caption,
              fontWeight: selected ? font.weight.bold : font.weight.medium,
              '&:hover': { bgcolor: color.pointTint },
            }}
          >
            {label[o]}
          </ButtonBase>
        );
      })}
    </Stack>
  );
}

/** UI-TP-09 이벤트 신청 - Tab3 (화면 설정) */
export default function ScreenStep({ screen, onChange, onPrev, onNext }: Props) {
  const setWidget = (w: ScreenWidget, patch: Partial<{ h: HAlign; v: VAlign }>) => onChange({ ...screen, widgets: { ...screen.widgets, [w]: { ...screen.widgets[w], ...patch } } });

  return (
    <>
      <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', lg: 'minmax(0, 1fr) minmax(0, 1fr)' }, alignItems: 'start' }}>
        {/* ① 화면 구성 위젯 */}
        <Card sx={{ p: 3 }}>
          <SectionTitle>화면 구성 위젯</SectionTitle>
          <Typography variant="body2" sx={{ mt: -1, mb: 2, color: text.secondary }}>
            요소마다 가로(좌·중·우)와 세로(상·중·하) 위치를 고르면 오른쪽 미리보기에 바로 반영됩니다.
          </Typography>

          <Box sx={{ display: 'grid', gridTemplateColumns: 'minmax(96px, 1fr) auto auto', columnGap: 2, rowGap: 1.25, alignItems: 'center' }}>
            <span />
            <Typography variant="body2" sx={{ color: text.tertiary }}>
              가로
            </Typography>
            <Typography variant="body2" sx={{ color: text.tertiary }}>
              세로
            </Typography>
            {WIDGET_ORDER.map((w) => (
              <Box key={w} sx={{ display: 'contents' }}>
                <Typography variant="body1" sx={{ fontWeight: font.weight.medium }}>
                  {widgetLabel[w]}
                </Typography>
                <Segmented options={H} value={screen.widgets[w].h} label={hLabel} onSelect={(h) => setWidget(w, { h })} ariaLabel={`${widgetLabel[w]} 가로 위치`} />
                <Segmented options={V} value={screen.widgets[w].v} label={vLabel} onSelect={(v) => setWidget(w, { v })} ariaLabel={`${widgetLabel[w]} 세로 위치`} />
              </Box>
            ))}
          </Box>

          <Typography variant="body1" sx={{ mt: 3, mb: 1, fontWeight: font.weight.medium }}>
            배경 테마
          </Typography>
          <Box role="radiogroup" aria-label="배경 테마" sx={{ display: 'grid', gap: 1, gridTemplateColumns: 'repeat(4, 1fr)' }}>
            {THEMES.map((t) => {
              const selected = screen.theme === t;
              const p = previewThemes[t];
              return (
                <ButtonBase
                  key={t}
                  role="radio"
                  aria-checked={selected}
                  onClick={() => onChange({ ...screen, theme: t })}
                  sx={{
                    flexDirection: 'column',
                    gap: 0.75,
                    p: 1,
                    borderRadius: radius.lg,
                    border: `1px solid ${selected ? color.point : line.base}`,
                    boxShadow: selected ? `inset 0 0 0 1px ${color.point}` : 'none',
                    bgcolor: color.surface,
                  }}
                >
                  <Box sx={{ width: '100%', height: 32, borderRadius: radius.sm, bgcolor: p.bg, border: `1px solid ${line.base}`, display: 'flex', alignItems: 'center', justifyContent: 'flex-end', px: 1 }}>
                    <Box sx={{ width: 20, height: 6, bgcolor: p.accent }} />
                  </Box>
                  <Typography variant="body2" sx={{ color: selected ? color.pointDeep : text.secondary, fontWeight: selected ? font.weight.semibold : font.weight.regular }}>
                    {themeLabel[t]}
                  </Typography>
                </ButtonBase>
              );
            })}
          </Box>

          <Box sx={{ mt: 2.5, px: 1.5, py: 1, borderRadius: radius.md, bgcolor: color.surface }}>
            <Typography variant="body2" sx={{ color: text.secondary }}>
              선택 요약 · {screenSummary(screen)}
            </Typography>
          </Box>
        </Card>

        {/* ② 미리보기 */}
        <Card sx={{ p: 3 }}>
          <SectionTitle
            action={
              <Box component="span" sx={{ px: 1, py: 0.25, borderRadius: radius.sm, border: `1px solid ${line.base}`, ...type.caption, color: text.tertiary }}>
                예시 그림
              </Box>
            }
          >
            미리보기
          </SectionTitle>
          <ScreenPreview screen={screen} />
          <Typography variant="body2" sx={{ mt: 1.5, color: text.tertiary, textAlign: 'center' }}>
            위치와 테마만 반영한 예시입니다. 실제 이벤트 화면은 승인 후 이 구성으로 생성됩니다.
          </Typography>
        </Card>
      </Box>

      {/* ③ 이전 단계로 · ④ 다음 단계로 (항상 활성) */}
      <Stack direction="row" justifyContent="space-between" sx={{ mt: 4 }}>
        <Button variant="outlined" size="large" startIcon={<ArrowBackRoundedIcon />} onClick={onPrev}>
          이전 단계로
        </Button>
        <Button variant="contained" size="large" onClick={onNext} sx={{ minWidth: 140 }}>
          다음 단계로
        </Button>
      </Stack>
    </>
  );
}
