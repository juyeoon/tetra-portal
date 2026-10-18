import { createTheme } from '@mui/material/styles';
import { color, font, ink, layout, line, radius, shadow, size, text, tones } from './tokens';

/**
 * MUI 테마 — 모든 값은 src/styles/tokens.css 의 CSS 변수를 참조한다.
 * 숫자나 색을 여기에 직접 쓰지 않는다 (예외: MUI 내부 rem 계산용 typography.fontSize).
 */
export * from './tokens';

const heading = (weight: keyof typeof font.headingWeight) => ({
  fontFamily: font.heading,
  fontWeight: font.headingWeight[weight],
  letterSpacing: font.letterSpacing.heading,
  color: color.ink,
});
const bodyType = (size: keyof typeof font.lineHeight, weight: keyof typeof font.weight = 'regular') => ({
  fontSize: font.size[size],
  lineHeight: font.lineHeight[size],
  fontWeight: font.weight[weight],
});

const theme = createTheme({
  // 팔레트에 CSS 변수를 쓰기 위해 CSS 상대 색상(color-mix) 모드를 사용
  cssVariables: { nativeColor: true },
  palette: {
    mode: 'light',
    primary: { main: color.navy, dark: color.navyDeep, light: color.periDeep, contrastText: color.white },
    secondary: { main: color.point, light: color.pointTint, dark: color.pointDeep, contrastText: color.onPoint },
    success: { main: tones.green.dot, light: tones.green.bg, dark: tones.green.fg, contrastText: color.white },
    warning: { main: tones.amber.dot, light: tones.amber.bg, dark: tones.amber.fg, contrastText: color.ink },
    error: { main: tones.red.dot, light: tones.red.bg, dark: tones.red.fg, contrastText: color.white },
    info: { main: color.peri, light: tones.blue.bg, dark: tones.blue.fg, contrastText: color.navy },
    text: { primary: text.primary, secondary: text.secondary, disabled: text.disabled },
    divider: line.base,
    background: { default: color.page, paper: color.surface },
    action: { hover: ink[5], selected: ink[5] },
  },
  spacing: (factor: number) => `calc(${factor} * ${layout.spaceUnit})`,
  shape: { borderRadius: 0 }, // 포털은 모서리 없음 (tokens.css --radius-*)
  typography: {
    fontFamily: font.body,
    fontSize: 14, // MUI 내부 rem 변환 기준값 (실제 크기는 아래 variant가 결정)
    h1: { ...heading('display'), fontSize: font.size.display, lineHeight: font.lineHeight.display },
    h2: { ...heading('page'), fontSize: font.size.page, lineHeight: font.lineHeight.page },
    h3: { ...heading('title'), fontSize: font.size.title, lineHeight: font.lineHeight.title },
    h4: { ...heading('section'), fontSize: font.size.body, lineHeight: font.lineHeight.body },
    h5: { ...heading('section'), fontSize: font.size.body, lineHeight: font.lineHeight.body },
    h6: { ...heading('section'), fontSize: font.size.body, lineHeight: font.lineHeight.body },
    subtitle1: bodyType('body', 'semibold'),
    subtitle2: bodyType('caption', 'bold'),
    body1: bodyType('body'),
    body2: bodyType('caption'),
    caption: { ...bodyType('caption'), color: text.tertiary },
    overline: { ...bodyType('caption', 'semibold'), letterSpacing: font.letterSpacing.overline, textTransform: 'none' },
    button: { ...bodyType('body', 'semibold'), textTransform: 'none', letterSpacing: 0 },
  },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        body: { backgroundColor: color.page, color: text.primary, WebkitFontSmoothing: 'antialiased' },
        '::selection': { background: color.pointLine },
      },
    },
    MuiButtonBase: { defaultProps: { disableRipple: true } },
    MuiButton: {
      defaultProps: { disableElevation: true, disableRipple: true },
      styleOverrides: {
        root: {
          borderRadius: radius.md,
          minHeight: size.control,
          paddingInline: 'calc(2.25 * var(--space-unit))',
          transition: 'background-color .15s, border-color .15s, color .15s',
          '&.Mui-focusVisible': { boxShadow: shadow.focus },
        },
        sizeSmall: { minHeight: size.controlSm, paddingInline: 'calc(1.5 * var(--space-unit))', fontSize: font.size.caption, lineHeight: font.lineHeight.caption },
        sizeLarge: { minHeight: size.controlLg, paddingInline: 'calc(3 * var(--space-unit))' },
        containedPrimary: { backgroundColor: color.navy, '&:hover': { backgroundColor: color.navyDeep } },
        contained: { '&.Mui-disabled': { backgroundColor: ink[5], color: text.disabled } },
        outlined: { borderColor: line.strong, color: text.primary, '&:hover': { borderColor: ink[40], backgroundColor: ink[5] } },
        outlinedPrimary: { borderColor: line.strong, color: color.navy },
        outlinedError: { borderColor: tones.red.dot, color: tones.red.fg, '&:hover': { backgroundColor: tones.red.bg, borderColor: tones.red.dot } },
        outlinedSuccess: { borderColor: tones.green.dot, color: tones.green.fg, '&:hover': { backgroundColor: tones.green.bg, borderColor: tones.green.dot } },
        text: { color: text.primary, '&:hover': { backgroundColor: ink[5] } },
      },
    },
    MuiIconButton: {
      defaultProps: { disableRipple: true },
      styleOverrides: { root: { borderRadius: radius.md, color: text.tertiary, '&:hover': { backgroundColor: ink[5], color: text.primary } } },
    },
    MuiPaper: { defaultProps: { elevation: 0 }, styleOverrides: { rounded: { borderRadius: radius.xl } } },
    MuiCard: {
      defaultProps: { elevation: 0 },
      // Snow: 테두리·그림자 없이 연한 배경으로 구분
      styleOverrides: { root: { backgroundColor: color.card, borderRadius: radius.xl, border: 0 } },
    },
    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          borderRadius: radius.md,
          backgroundColor: color.surface,
          fontSize: font.size.body,
          lineHeight: font.lineHeight.body,
          '& .MuiOutlinedInput-notchedOutline': { borderColor: line.base },
          '&:hover .MuiOutlinedInput-notchedOutline': { borderColor: line.strong },
          '&.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: shadow.focusColor, borderWidth: 1 },
          '&.Mui-focused': { boxShadow: shadow.focusSoft },
          '&.Mui-error .MuiOutlinedInput-notchedOutline': { borderColor: tones.red.dot },
          '&.Mui-disabled': { backgroundColor: ink[5] },
          '&.Mui-disabled .MuiOutlinedInput-notchedOutline': { borderColor: 'transparent' },
        },
        input: {
          height: font.lineHeight.body,
          paddingBlock: 'calc((var(--control-h) - var(--lh-body)) / 2)',
          paddingInline: 'calc(1.75 * var(--space-unit))',
          '&::placeholder': { color: text.tertiary, opacity: 1 },
        },
        inputMultiline: { paddingBlock: 0, paddingInline: 0 },
        multiline: { padding: 'calc(1.5 * var(--space-unit)) calc(1.75 * var(--space-unit))' },
      },
    },
    MuiFormHelperText: { styleOverrides: { root: { marginLeft: 0, marginTop: 'calc(0.5 * var(--space-unit))', ...bodyType('caption'), color: text.tertiary } } },
    MuiTabs: {
      styleOverrides: {
        root: { minHeight: size.tab, borderBottom: `1px solid ${line.base}` },
        // 웹폰트 로딩 후 폭이 바뀌어도 어긋나지 않도록 인디케이터는 Tab의 ::after로 그린다
        indicator: { display: 'none' },
      },
    },
    MuiTab: {
      styleOverrides: {
        root: {
          minHeight: size.tab,
          minWidth: 0,
          paddingInline: 0,
          marginRight: 'calc(3.5 * var(--space-unit))',
          ...bodyType('body', 'medium'),
          color: text.tertiary,
          position: 'relative',
          '&:hover': { color: text.primary },
          '&.Mui-selected': { color: text.primary, fontWeight: font.weight.semibold },
          '&.Mui-selected::after': {
            content: '""',
            position: 'absolute',
            left: 0,
            right: 0,
            bottom: -1,
            height: 2,
            backgroundColor: color.point,
          },
        },
      },
    },
    MuiTableCell: {
      styleOverrides: {
        root: { borderColor: line.base, padding: 'calc(1.75 * var(--space-unit)) calc(1.5 * var(--space-unit))', ...bodyType('body'), color: text.primary },
        head: { ...bodyType('caption', 'medium'), color: text.tertiary, whiteSpace: 'nowrap', borderBottom: `1px solid ${line.strong}`, paddingBlock: 'var(--space-unit)' },
      },
    },
    MuiTableRow: {
      styleOverrides: {
        root: { '&:hover > td': { backgroundColor: color.card }, '&:last-of-type td': { borderBottom: 0 } },
        head: { '&:hover > th': { backgroundColor: 'transparent' } },
      },
    },
    MuiDialog: {
      styleOverrides: {
        paper: { borderRadius: radius.xl, padding: 'var(--space-unit)', outline: 'none', boxShadow: shadow.modal },
        container: { outline: 'none' },
      },
    },
    MuiDialogTitle: { styleOverrides: { root: { ...heading('title'), fontSize: font.size.title, lineHeight: font.lineHeight.title, padding: 'calc(2 * var(--space-unit)) calc(2 * var(--space-unit)) calc(0.5 * var(--space-unit))' } } },
    MuiDialogContent: { styleOverrides: { root: { padding: 'var(--space-unit) calc(2 * var(--space-unit))' } } },
    MuiDialogActions: { styleOverrides: { root: { padding: 'calc(2 * var(--space-unit))', gap: 'var(--space-unit)', '& > :not(style) ~ :not(style)': { marginLeft: 0 } } } },
    MuiBackdrop: { styleOverrides: { root: { '&:not(.MuiBackdrop-invisible)': { backgroundColor: shadow.backdrop } } } },
    MuiLink: {
      defaultProps: { underline: 'always' },
      styleOverrides: {
        root: {
          color: text.primary,
          cursor: 'pointer',
          textUnderlineOffset: 3,
          textDecorationColor: line.strong,
          transition: 'color .15s, text-decoration-color .15s',
          '&:hover': { color: color.pointDeep, textDecorationColor: color.point },
        },
      },
    },
    MuiTooltip: {
      styleOverrides: { tooltip: { backgroundColor: ink[88], ...bodyType('caption'), borderRadius: radius.md, padding: 'calc(0.5 * var(--space-unit)) var(--space-unit)' } },
    },
    MuiAvatar: { defaultProps: { variant: 'square' } },
    MuiSwitch: { styleOverrides: { track: { borderRadius: radius.md }, thumb: { borderRadius: radius.md } } },
    MuiMenu: { styleOverrides: { paper: { borderRadius: radius.lg, boxShadow: shadow.float } } },
    MuiMenuItem: { styleOverrides: { root: { ...bodyType('body'), borderRadius: radius.md, margin: '0 calc(0.5 * var(--space-unit))', minHeight: size.control } } },
    MuiAlert: {
      styleOverrides: {
        root: { borderRadius: radius.lg, ...bodyType('body') },
        // 상태 톤과 같은 파스텔 배경 + 진한 글자
        standardSuccess: { backgroundColor: tones.green.bg, color: tones.green.fg, '& .MuiAlert-icon': { color: tones.green.dot } },
        standardError: { backgroundColor: tones.red.bg, color: tones.red.fg, '& .MuiAlert-icon': { color: tones.red.dot } },
        standardWarning: { backgroundColor: tones.amber.bg, color: tones.amber.fg, '& .MuiAlert-icon': { color: tones.amber.dot } },
        standardInfo: { backgroundColor: tones.blue.bg, color: tones.blue.fg, '& .MuiAlert-icon': { color: tones.blue.dot } },
      },
    },
    MuiSnackbarContent: { styleOverrides: { root: { borderRadius: radius.lg } } },
  },
});

export default theme;
