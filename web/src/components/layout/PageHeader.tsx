import { Fragment, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { Link as RouterLink } from 'react-router-dom';
import { Box, IconButton, Link, Stack, Typography } from '@mui/material';
import ArrowBackRoundedIcon from '@mui/icons-material/ArrowBackRounded';
import MenuRoundedIcon from '@mui/icons-material/MenuRounded';
import { color, font, layout, size, text, type } from '@/theme/theme';
import { useLayout } from './LayoutContext';
import UserMenu from './UserMenu';

interface PageHeaderProps {
  /** 제목 위 작은 영문 라벨 (예: EVENTS) */
  eyebrow: string;
  /** 현재 페이지까지의 경로. title이 없으면 마지막 항목이 제목이 된다 */
  breadcrumbs: string[];
  back?: { to: string; label: string };
  /** 페이지 제목 (상세 화면의 이벤트명 등) */
  title?: ReactNode;
  /** 제목 오른쪽 영역 */
  actions?: ReactNode;
}

/**
 * 흰 상단바 — 영문 라벨 · 경로 › 페이지 제목(h2) · 사용자 메뉴.
 * 레이아웃의 상단바 영역에 portal로 렌더링되어 가로 전체를 채운다.
 * 상위 경로는 제목과 같은 줄에 흐리게 표시한다 (화면설계서: "이벤트 내역 > 상세").
 */
export default function PageHeader({ eyebrow, breadcrumbs, back, title, actions }: PageHeaderProps) {
  const { openNav, topbar } = useLayout();
  if (!topbar) return null;

  const pageTitle = title ?? breadcrumbs[breadcrumbs.length - 1];
  const path = title ? breadcrumbs : breadcrumbs.slice(0, -1);

  return createPortal(
    <Box sx={{ maxWidth: layout.pageMaxWidth, mx: 'auto', px: { xs: layout.pagePaddingXMobile, md: layout.pagePaddingX }, py: layout.topbarPaddingY }}>
      <Stack direction="row" alignItems="center" spacing={1}>
        <IconButton onClick={openNav} sx={{ display: { md: 'none' }, ml: -1 }} aria-label="메뉴 열기">
          <MenuRoundedIcon />
        </IconButton>

        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Typography variant="overline" component="p" sx={{ display: 'block', color: color.pointDeep, lineHeight: 1.4 }}>
            {eyebrow}
          </Typography>
          {back && (
            <Link
              component={RouterLink}
              to={back.to}
              underline="none"
              sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5, mt: 0.25, ...type.caption, color: text.secondary, '&:hover': { color: text.primary } }}
            >
              <ArrowBackRoundedIcon sx={{ fontSize: size.iconSm }} />
              {back.label}
            </Link>
          )}
          <Typography variant="h2" component="h1" sx={{ mt: 0.25 }}>
            {path.map((crumb) => (
              <Fragment key={crumb}>
                <Box component="span" sx={{ color: text.tertiary, fontWeight: font.weight.medium }}>
                  {crumb}
                </Box>
                <Box component="span" aria-hidden sx={{ mx: 1, color: text.disabled, fontWeight: font.weight.regular }}>
                  ›
                </Box>
              </Fragment>
            ))}
            {pageTitle}
          </Typography>
        </Box>

        <Stack alignItems="flex-end" justifyContent="space-between" spacing={1.5} sx={{ alignSelf: 'stretch' }}>
          <UserMenu />
          {actions && <Box sx={{ display: { xs: 'none', md: 'block' } }}>{actions}</Box>}
        </Stack>
      </Stack>
      {/* 좁은 화면에서는 액션을 제목 아래로 */}
      {actions && <Box sx={{ display: { xs: 'block', md: 'none' }, mt: 1.5 }}>{actions}</Box>}
    </Box>,
    topbar,
  );
}
