import { useState, type FormEvent, type ReactNode } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Alert, Box, Button, Dialog, DialogContent, Stack, TextField, Typography } from '@mui/material';
import CheckCircleOutlineRoundedIcon from '@mui/icons-material/CheckCircleOutlineRounded';
import CheckRoundedIcon from '@mui/icons-material/CheckRounded';
import RadioButtonUncheckedRoundedIcon from '@mui/icons-material/RadioButtonUncheckedRounded';
import logo from '@/assets/logo-navy.png';
import FormField from '@/components/common/FormField';
import { useAppData } from '@/store/AppDataContext';
import { color, font, radius, size, text, tones, type } from '@/theme/theme';

/** 포털 메뉴 없이 단독 페이지 */
function Shell({ children }: { children: ReactNode }) {
  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', px: 2, py: 6 }}>
      <Box sx={{ width: '100%', maxWidth: size.contentNarrow, p: { xs: 3, sm: 4 }, borderRadius: radius.xl, bgcolor: color.card }}>
        <Stack direction="row" alignItems="flex-end" spacing={0.75} sx={{ mb: 2 }}>
          <Box component="img" src={logo} alt="TETRA" sx={{ height: 22, display: 'block' }} />
          <Typography component="span" sx={{ ...type.caption, fontFamily: font.heading, fontWeight: font.weight.bold, letterSpacing: font.letterSpacing.overline, color: color.pointDeep, lineHeight: 1 }}>
            PORTAL
          </Typography>
        </Stack>
        <Typography variant="h2" component="h1">
          비밀번호 설정
        </Typography>
        {children}
      </Box>
    </Box>
  );
}

/** UI-TP-04 비밀번호 설정 — 이메일로 받은 비밀번호 변경 URL로 진입 (?token=…&email=…) */
export default function PasswordSetupPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const { setupPassword } = useAppData();
  // TODO: 토큰 검증 API 연동. 지금은 token이 없거나 'expired'면 만료로 처리
  const token = params.get('token');
  const email = params.get('email') ?? 'admin@tenant-company.com';
  const [expired, setExpired] = useState(!token || token === 'expired');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [saving, setSaving] = useState(false);
  const [done, setDone] = useState(false);

  if (expired) {
    return (
      <Shell>
        <Alert severity="error" sx={{ mt: 3 }}>
          링크가 만료되었거나 이미 사용되었습니다.
          <br />
          로그인 화면의 고객센터로 문의해주세요.
        </Alert>
        <Button variant="outlined" fullWidth size="large" sx={{ mt: 3 }} onClick={() => navigate('/login')}>
          로그인 화면으로
        </Button>
      </Shell>
    );
  }

  // 8자 이상 + 두 값 일치 시 저장 가능 (문자 조합 규칙은 미확정)
  const longEnough = password.length >= 8;
  const matches = password.length > 0 && password === confirm;
  const canSave = longEnough && matches && !saving;

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!canSave || !token) return;
    setSaving(true);
    const ok = await setupPassword(token, password);
    setSaving(false);
    if (ok) setDone(true);
    else setExpired(true);
  };

  return (
    <Shell>
      <Typography variant="body1" sx={{ mt: 0.5, color: text.secondary }}>
        <Box component="span" sx={{ color: text.primary, fontWeight: font.weight.semibold }}>
          {email}
        </Box>{' '}
        계정의 비밀번호를 설정합니다.
      </Typography>

      <Box component="form" onSubmit={handleSubmit} noValidate sx={{ mt: 3 }}>
        <Stack spacing={2}>
          <FormField label="새 비밀번호" htmlFor="new-pw" required>
            <TextField
              id="new-pw"
              type="password"
              fullWidth
              autoComplete="new-password"
              placeholder="8자 이상 입력"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              error={password.length > 0 && !longEnough}
              helperText={password.length > 0 && !longEnough ? '8자 이상 입력해주세요.' : undefined}
            />
          </FormField>
          <FormField label="비밀번호 확인" htmlFor="confirm-pw" required>
            <TextField
              id="confirm-pw"
              type="password"
              fullWidth
              autoComplete="new-password"
              placeholder="비밀번호를 한 번 더 입력"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              error={confirm.length > 0 && !matches}
              helperText={confirm.length > 0 && !matches ? '비밀번호가 일치하지 않습니다.' : undefined}
            />
          </FormField>
        </Stack>

        <Stack direction="row" spacing={0.5} alignItems="center" sx={{ mt: 1.5, color: longEnough && matches ? tones.green.fg : text.tertiary }}>
          {longEnough && matches ? <CheckCircleOutlineRoundedIcon sx={{ fontSize: size.iconSm }} /> : <RadioButtonUncheckedRoundedIcon sx={{ fontSize: size.iconSm }} />}
          <Typography variant="body2" sx={{ color: 'inherit' }}>
            두 비밀번호가 일치해야 저장 버튼이 활성화됩니다.
          </Typography>
        </Stack>

        <Button type="submit" variant="contained" size="large" fullWidth disabled={!canSave} sx={{ mt: 3 }}>
          저장
        </Button>
      </Box>

      <Dialog open={done} maxWidth="xs" fullWidth>
        <DialogContent sx={{ textAlign: 'center', pt: 3 }}>
          <Box sx={{ width: size.iconBoxLg, height: size.iconBoxLg, mx: 'auto', mb: 2, borderRadius: radius.lg, display: 'grid', placeItems: 'center', bgcolor: tones.green.bg, color: tones.green.fg }}>
            <CheckRoundedIcon />
          </Box>
          <Typography variant="h3">비밀번호가 저장되었습니다</Typography>
          <Typography variant="body1" sx={{ mt: 0.5, color: text.secondary }}>
            확인을 누르면 로그인 화면으로 이동합니다.
          </Typography>
          <Button variant="contained" fullWidth sx={{ mt: 3 }} onClick={() => navigate('/login')}>
            확인
          </Button>
        </DialogContent>
      </Dialog>
    </Shell>
  );
}
