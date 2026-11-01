/**
 * 디자인 토큰 — src/styles/tokens.css 의 CSS 변수를 TS 이름으로 연결한다.
 * 값은 이 파일에 두지 않는다. 디자인 수정은 tokens.css에서만 한다.
 */
const v = (name: string) => `var(--${name})`;

export const color = {
  point: v('color-point'),
  pointDeep: v('color-point-deep'),
  pointTint: v('color-point-tint'),
  pointLine: v('color-point-line'),
  onPoint: v('color-on-point'),
  navy: v('color-navy'),
  navyDeep: v('color-navy-deep'),
  peri: v('color-peri'),
  periDeep: v('color-peri-deep'),
  periTint: v('color-peri-tint'),
  periLine: v('color-peri-line'),
  blueTint: v('color-blue-tint'),
  mist: v('color-mist'),
  ink: v('color-ink'),
  page: v('color-page'),
  card: v('color-card'),
  surface: v('color-surface'),
  white: '#fff',
};

/** ink 투명도 단계 */
export const ink = {
  88: v('ink-88'),
  64: v('ink-64'),
  40: v('ink-40'),
  24: v('ink-24'),
  20: v('ink-20'),
  10: v('ink-10'),
  5: v('ink-5'),
};

export const text = {
  primary: v('text-primary'),
  secondary: v('text-secondary'),
  tertiary: v('text-tertiary'),
  disabled: v('text-disabled'),
};

export const line = {
  strong: v('line-strong'),
  base: v('line-base'),
  soft: v('line-soft'),
};

export const onNavy = {
  primary: v('on-navy-primary'),
  secondary: v('on-navy-secondary'),
  tertiary: v('on-navy-tertiary'),
  line: v('on-navy-line'),
  hover: v('on-navy-hover'),
  active: v('on-navy-active'),
};

const tone = (name: string) => ({ bg: v(`tone-${name}-bg`), fg: v(`tone-${name}-fg`), dot: v(`tone-${name}-dot`) });

/** 상태 배지 톤 */
export const tones = {
  blue: tone('blue'),
  amber: tone('amber'),
  green: tone('green'),
  red: tone('red'),
  grey: tone('grey'),
} as const;

export type Tone = keyof typeof tones;

export const chartColors = {
  primary: v('chart-1'),
  secondary: v('chart-2'),
  tertiary: v('chart-3'),
  neutral: v('chart-neutral'),
  series: [v('chart-1'), v('chart-2'), v('chart-3')],
  issued: v('chart-issued'),
  soldOut: v('chart-soldout'),
  dropped: v('chart-dropped'),
};

/** 이벤트 화면 배경 테마 미리보기 */
const previewTheme = (name: string) => ({ bg: v(`preview-${name}-bg`), fg: v(`preview-${name}-fg`), accent: v(`preview-${name}-accent`) });
export const previewThemes = {
  WHITE: previewTheme('white'),
  WARM_ORANGE: previewTheme('orange'),
  DARK: previewTheme('dark'),
  BLUE: previewTheme('blue'),
};

export const code = { bg: v('code-bg'), fg: v('code-fg'), muted: v('code-muted') };

export const font = {
  heading: v('font-heading'),
  body: v('font-body'),
  mono: v('font-mono'),
  size: {
    display: v('fs-display'),
    page: v('fs-page'),
    title: v('fs-title'),
    body: v('fs-body'),
    caption: v('fs-caption'),
    mono: v('fs-mono'),
    monoSm: v('fs-mono-sm'),
    mask: v('fs-mask'),
  },
  lineHeight: {
    display: v('lh-display'),
    page: v('lh-page'),
    title: v('lh-title'),
    body: v('lh-body'),
    caption: v('lh-caption'),
  },
  /** 본문(프리젠테이션) 굵기 */
  weight: { light: v('fw-light'), regular: v('fw-regular'), medium: v('fw-medium'), semibold: v('fw-semibold'), bold: v('fw-bold') },
  /** 제목(페이퍼로지) 굵기 — h1 display / h2 page / h3 title / h4~h6 section */
  headingWeight: { display: v('fw-heading-display'), page: v('fw-heading-page'), title: v('fw-heading-title'), section: v('fw-heading-section') },
  letterSpacing: { heading: v('ls-heading'), overline: v('ls-overline') },
};

/** 하위 호환용 별칭 */
export const HEADING_FONT = font.heading;
export const BODY_FONT = font.body;
export const MONO_FONT = font.mono;

export const radius = { sm: v('radius-sm'), md: v('radius-md'), lg: v('radius-lg'), xl: v('radius-xl') };

export const shadow = {
  float: v('shadow-float'),
  modal: v('shadow-modal'),
  focusColor: v('focus-color'),
  focus: v('focus-ring'),
  focusSoft: v('focus-ring-soft'),
  backdrop: v('backdrop'),
};

/** 사이드바 (남색) */
export const sidebar = {
  radius: v('sidebar-radius'),
  bg: v('sidebar-bg'),
  brandAccent: v('sidebar-brand-accent'),
  text: v('sidebar-text'),
  icon: v('sidebar-icon'),
  disabled: v('sidebar-disabled'),
  hover: v('sidebar-hover'),
  activeBg: v('sidebar-active-bg'),
  activeText: v('sidebar-active-text'),
  activeIcon: v('sidebar-active-icon'),
  patternOpacity: v('sidebar-pattern-opacity'),
  /** 알림 센터 메뉴의 안 읽은 알림 수 */
  badgeH: v('sidebar-badge-h'),
  badgePadX: v('sidebar-badge-pad-x'),
  badgeFs: v('sidebar-badge-fs'),
  badgeBg: v('sidebar-badge-bg'),
  badgeFg: v('sidebar-badge-fg'),
  badgeActiveBg: v('sidebar-badge-active-bg'),
  badgeActiveFg: v('sidebar-badge-active-fg'),
};

export const layout = {
  sidebarWidth: v('sidebar-width'),
  pageMaxWidth: v('page-max-width'),
  pagePaddingX: v('page-padding-x'),
  pagePaddingXMobile: v('page-padding-x-mobile'),
  cardPadding: v('card-padding'),
  cardGap: v('card-gap'),
  topbarBg: v('topbar-bg'),
  topbarBorder: v('topbar-border'),
  topbarPaddingY: v('topbar-padding-y'),
  spaceUnit: v('space-unit'),
};

export const size = {
  control: v('control-h'),
  controlSm: v('control-h-sm'),
  controlLg: v('control-h-lg'),
  navItem: v('nav-item-h'),
  tab: v('tab-h'),
  rowMin: v('row-min-h'),
  badge: v('badge-h'),
  planBadge: v('plan-badge'),
  iconBox: v('icon-box'),
  iconBoxLg: v('icon-box-lg'),
  avatarSm: v('avatar-sm'),
  avatarMd: v('avatar-md'),
  dot: v('dot'),
  iconSm: v('icon-sm'),
  iconMd: v('icon-md'),
  iconLg: v('icon-lg'),
  flowNode: v('flow-node'),
  infoLabel: v('info-label-w'),
  infoLabelWide: v('info-label-w-wide'),
  infoLabelMobile: v('info-label-w-mobile'),
  contentNarrow: v('content-narrow'),
  contentForm: v('content-form'),
  contentText: v('content-text'),
  modalSm: v('modal-sm'),
};

/** KPI 카드 강조 */
export const kpi = {
  labelColor: v('kpi-label-color'),
  valueBg: v('kpi-value-bg'),
  valueFg: v('kpi-value-fg'),
  valuePadX: v('kpi-value-pad-x'),
  valueRadius: v('kpi-value-radius'),
  valueFluid: v('kpi-value-fluid'),
};

/** 자주 쓰는 글자 묶음 (sx에 펼쳐서 사용) */
export const type = {
  caption: { fontSize: font.size.caption, lineHeight: font.lineHeight.caption },
  body: { fontSize: font.size.body, lineHeight: font.lineHeight.body },
  mono: { fontFamily: font.mono, fontSize: font.size.mono },
  monoSm: { fontFamily: font.mono, fontSize: font.size.monoSm },
  mask: { fontSize: font.size.mask, letterSpacing: 2 },
};
