import { useEffect, useMemo, useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { Box, Drawer, useMediaQuery, useTheme } from '@mui/material';
import { layout } from '@/theme/theme';
import { LayoutContext } from './LayoutContext';
import Sidebar from './Sidebar';

export default function AppLayout() {
  const theme = useTheme();
  const desktop = useMediaQuery(theme.breakpoints.up('md'));
  const [mobileOpen, setMobileOpen] = useState(false);
  const [topbar, setTopbar] = useState<HTMLElement | null>(null);
  const { pathname } = useLocation();

  useEffect(() => {
    window.scrollTo(0, 0);
  }, [pathname]);

  const layoutCtx = useMemo(() => ({ openNav: () => setMobileOpen(true), topbar }), [topbar]);

  return (
    <LayoutContext.Provider value={layoutCtx}>
      <Box sx={{ display: 'flex', minHeight: '100vh' }}>
        {desktop ? (
          <Box component="aside" sx={{ width: layout.sidebarWidth, flexShrink: 0 }}>
            <Box sx={{ position: 'fixed', inset: '0 auto 0 0' }}>
              <Sidebar />
            </Box>
          </Box>
        ) : (
          <Drawer open={mobileOpen} onClose={() => setMobileOpen(false)} slotProps={{ paper: { sx: { border: 0 } } }}>
            <Sidebar onNavigate={() => setMobileOpen(false)} />
          </Drawer>
        )}
        <Box component="main" sx={{ flex: 1, minWidth: 0 }}>
          {/* 상단바 — 콘텐츠 폭과 무관하게 가로 전체를 채운다 (내용은 PageHeader가 채움) */}
          <Box ref={setTopbar} component="header" sx={{ bgcolor: layout.topbarBg, borderBottom: `1px solid ${layout.topbarBorder}` }} />
          <Box sx={{ maxWidth: layout.pageMaxWidth, mx: 'auto', px: { xs: layout.pagePaddingXMobile, md: layout.pagePaddingX }, pt: 3, pb: 6 }}>
            <Outlet />
          </Box>
        </Box>
      </Box>
    </LayoutContext.Provider>
  );
}
