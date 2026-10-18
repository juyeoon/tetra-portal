import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Avatar, Box, ButtonBase, Divider, ListItemIcon, Menu, MenuItem, Typography } from '@mui/material';
import KeyboardArrowDownRoundedIcon from '@mui/icons-material/KeyboardArrowDownRounded';
import LogoutOutlinedIcon from '@mui/icons-material/LogoutOutlined';
import SettingsOutlinedIcon from '@mui/icons-material/SettingsOutlined';
import { useAppData } from '@/store/AppDataContext';
import { color, font, ink, radius, size, text, tones, type } from '@/theme/theme';

/** 상단 우측 "테넌트컴퍼니 님 ▾" */
export default function UserMenu() {
  const navigate = useNavigate();
  const { account } = useAppData();
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);

  return (
    <>
      <ButtonBase
        onClick={(e) => setAnchor(e.currentTarget)}
        sx={{ gap: 1, px: 1, py: 0.5, borderRadius: radius.md, '&:hover': { bgcolor: ink[5] } }}
      >
        <Avatar sx={{ width: size.avatarSm, height: size.avatarSm, bgcolor: color.pointTint, color: color.pointDeep, ...type.caption, fontWeight: font.weight.semibold }}>
          {account.companyName.slice(0, 1)}
        </Avatar>
        <Typography variant="body1" sx={{ display: { xs: 'none', sm: 'block' }, whiteSpace: 'nowrap', fontWeight: font.weight.medium }}>
          {account.companyName} 님
        </Typography>
        <KeyboardArrowDownRoundedIcon sx={{ fontSize: size.iconSm, color: text.tertiary }} />
      </ButtonBase>
      <Menu
        anchorEl={anchor}
        open={!!anchor}
        onClose={() => setAnchor(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
        slotProps={{ paper: { sx: { mt: 0.5, minWidth: 220 } } }}
      >
        <Box sx={{ px: 2, py: 1 }}>
          <Typography variant="subtitle1">{account.companyName}</Typography>
          <Typography variant="caption">{account.email}</Typography>
        </Box>
        <Divider sx={{ my: 0.5 }} />
        <MenuItem
          onClick={() => {
            setAnchor(null);
            navigate('/settings');
          }}
        >
          <ListItemIcon>
            <SettingsOutlinedIcon sx={{ fontSize: size.iconMd }} />
          </ListItemIcon>
          설정
        </MenuItem>
        {/* TODO: 로그아웃 API 연동 */}
        <MenuItem onClick={() => navigate('/login')} sx={{ color: tones.red.fg }}>
          <ListItemIcon sx={{ color: 'inherit' }}>
            <LogoutOutlinedIcon sx={{ fontSize: size.iconMd }} />
          </ListItemIcon>
          로그아웃
        </MenuItem>
      </Menu>
    </>
  );
}
