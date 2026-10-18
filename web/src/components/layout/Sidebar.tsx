import type { ReactNode } from 'react';
import { Link, NavLink, useLocation } from 'react-router-dom';
import { Box, ButtonBase, Typography } from '@mui/material';
import ListAltOutlinedIcon from '@mui/icons-material/ListAltOutlined';
import EditCalendarOutlinedIcon from '@mui/icons-material/EditCalendarOutlined';
import ShowChartOutlinedIcon from '@mui/icons-material/ShowChartOutlined';
import AssessmentOutlinedIcon from '@mui/icons-material/AssessmentOutlined';
import NotificationsNoneOutlinedIcon from '@mui/icons-material/NotificationsNoneOutlined';
import SettingsOutlinedIcon from '@mui/icons-material/SettingsOutlined';
import logoWhite from '@/assets/logo-white.png';
import wavePattern from '@/assets/wave-pattern.png';
import { font, layout, onNavy, radius, sidebar, size, type } from '@/theme/theme';

interface NavItem {
  label: string;
  /** 없으면 비활성(준비중) 메뉴 */
  to?: string;
  icon: ReactNode;
  /** NavLink 기본 매칭 대신 쓸 활성 조건 */
  isActive?: (pathname: string) => boolean;
}

/** 화면설계서 공통 UI 요소 — 사이드바 내비게이션 순서 */
const NAV: NavItem[] = [
  { label: '이벤트 내역', to: '/events', icon: <ListAltOutlinedIcon />, isActive: (p) => p.startsWith('/events') && p !== '/events/new' },
  { label: '이벤트 신청', to: '/events/new', icon: <EditCalendarOutlinedIcon /> },
  // UI-TP-11: 기획 단계 — 비활성(준비중) 메뉴로만 노출
  { label: '모니터링', icon: <ShowChartOutlinedIcon /> },
  { label: '리포트', to: '/reports', icon: <AssessmentOutlinedIcon /> },
  { label: '알림 센터', to: '/notifications', icon: <NotificationsNoneOutlinedIcon /> },
  { label: '설정', to: '/settings', icon: <SettingsOutlinedIcon /> },
];

const itemSx = {
  width: '100%',
  height: size.navItem,
  justifyContent: 'flex-start',
  gap: 1,
  px: 1.5,
  color: sidebar.text,
  ...type.body,
  fontWeight: font.weight.medium,
  transition: 'background-color .15s, color .15s',
  '& svg': { fontSize: size.iconMd, color: sidebar.icon, transition: 'color .15s' },
} as const;

/**
 * 남색 사이드바 (240px) — 콘솔과 같은 틀(흰 로고 + 영문 표기, 같은 여백·메뉴 크기).
 * 구분: 활성 메뉴는 흰 배경 + 남색 글자 + 페리윙클 아이콘, 하단에 로그인 화면과 같은 물결 무늬.
 * (콘솔: 반투명 배경 + 페리윙클 바, 체커 무늬)
 */
export default function Sidebar({ onNavigate }: { onNavigate?: () => void }) {
  const { pathname } = useLocation();

  return (
    <Box
      sx={{
        position: 'relative',
        overflow: 'hidden',
        width: layout.sidebarWidth,
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        borderRadius: sidebar.radius,
        bgcolor: sidebar.bg,
      }}
    >
      {/* 물결 무늬 — 흰 선 PNG를 옅게 */}
      <Box
        aria-hidden
        sx={{
          position: 'absolute',
          left: -120,
          right: -100,
          bottom: -60,
          height: 420,
          backgroundImage: `url(${wavePattern})`,
          backgroundSize: 'cover',
          backgroundPosition: 'center',
          opacity: sidebar.patternOpacity,
          pointerEvents: 'none',
        }}
      />

      {/* 로고 — 클릭 시 Portal 홈(이벤트 내역) */}
      <ButtonBase
        component={Link}
        to="/events"
        onClick={onNavigate}
        aria-label="Portal 홈(이벤트 내역)으로 이동"
        sx={{ position: 'relative', justifyContent: 'flex-start', alignItems: 'flex-end', gap: 1, mx: 2, mt: 3, mb: 3, px: 1.5, py: 1, borderRadius: radius.md, '&:hover': { bgcolor: sidebar.hover } }}
      >
        <Box component="img" src={logoWhite} alt="TETRA" sx={{ height: 26, display: 'block' }} />
        <Typography component="span" sx={{ ...type.caption, fontFamily: font.heading, fontWeight: font.weight.bold, letterSpacing: font.letterSpacing.overline, color: sidebar.brandAccent, lineHeight: 1, pb: '2px' }}>
          PORTAL
        </Typography>
      </ButtonBase>

      <Box component="nav" sx={{ position: 'relative', flex: 1, overflowY: 'auto', px: 2, pb: 2, display: 'flex', flexDirection: 'column', gap: 0.5 }}>
        {NAV.map((item) => {
          if (!item.to) {
            return (
              <Box key={item.label} aria-disabled sx={{ ...itemSx, display: 'flex', alignItems: 'center', color: sidebar.disabled, cursor: 'default', '& svg': { fontSize: size.iconMd, color: sidebar.disabled } }}>
                {item.icon}
                {item.label}
                <Box component="span" sx={{ ml: 'auto', px: 0.75, borderRadius: radius.sm, border: `1px solid ${onNavy.line}`, ...type.caption, color: sidebar.disabled }}>
                  준비중
                </Box>
              </Box>
            );
          }
          const forcedActive = item.isActive?.(pathname);
          return (
            <ButtonBase
              key={item.to}
              component={NavLink}
              to={item.to}
              end
              onClick={onNavigate}
              className={forcedActive ? 'active' : undefined}
              sx={{
                ...itemSx,
                '&:hover': { bgcolor: sidebar.hover, color: onNavy.primary },
                '&.active': {
                  bgcolor: sidebar.activeBg,
                  color: sidebar.activeText,
                  fontWeight: font.weight.semibold,
                  '& svg': { color: sidebar.activeIcon },
                },
              }}
            >
              {item.icon}
              {item.label}
            </ButtonBase>
          );
        })}
      </Box>
    </Box>
  );
}
