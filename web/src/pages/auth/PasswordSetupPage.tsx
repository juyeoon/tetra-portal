import { useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Alert, Box, Button, CircularProgress, Dialog, DialogContent, Stack, TextField, Typography } from '@mui/material';
import CheckCircleOutlineRoundedIcon from '@mui/icons-material/CheckCircleOutlineRounded';
import CheckRoundedIcon from '@mui/icons-material/CheckRounded';
import RadioButtonUncheckedRoundedIcon from '@mui/icons-material/RadioButtonUncheckedRounded';
import logo from '@/assets/logo-navy.png';
import FormField from '@/components/common/FormField';
import { setupPassword, verifyPasswordLink } from '@/api/auth';
import { ApiError, errorMessage } from '@/api/client';
import { useToast } from '@/store/ToastContext';
import { color, font, radius, size, text, tones, type } from '@/theme/theme';
import { readPasswordLink } from './passwordLink';

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

const MIN_LENGTH = 8;
const MAX_LENGTH = 64;

/** UI-TP-04 비밀번호 설정 — Console이 보낸 메일 링크(#t=&exp=&sig= 또는 ?t=&exp=&sig=)로 진입. 들어오면 링크를 확인해 이메일을 보여 준다 */
export default function PasswordSetupPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const toast = useToast();
  /** 처음 렌더 때 한 번만 읽는다 (아래에서 주소를 지움). 하나라도 없으면 null (만료 화면) */
  const [link] = useState(() => readPasswordLink(location.hash, location.search));
  /** 확인 전 null */
  const [email, setEmail] = useState<string | null>(null);
  const [expired, setExpired] = useState(!link);
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [saving, setSaving] = useState(false);
  const [done, setDone] = useState(false);
  /** 서버가 돌려준 항목별 사유 (400) */
  const [serverError, setServerError] = useState<string | null>(null);

  // 서명이 브라우저 기록 · Referer에 남지 않게 주소를 /password/setup만 남긴다 (링크 확인 요청보다 먼저).
  // 새로 고침하면 값이 없어 만료 화면 — 메일 링크를 다시 누르면 된다
  useEffect(() => {
    if (location.hash || location.search) navigate(location.pathname, { replace: true });
  }, [location.hash, location.search, location.pathname, navigate]);

  useEffect(() => {
    if (!link) return;
    let cancelled = false;
    verifyPasswordLink(link)
      .then((r) => !cancelled && setEmail(r.email))
      .catch((e) => {
        if (cancelled) return;
        // 410 만료 · 사용됨 · 위조, 400 형식 오류 모두 만료 화면. 서버에 닿지 못했을 때만 안내 후 만료 화면
        if (!(e instanceof ApiError) || e.status >= 500 || e.status === 0) toast(errorMessage(e), 'error');
        setExpired(true);
      });
    return () => {
      cancelled = true;
    };
  }, [link, toast]);

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

  if (email === null) {
    return (
      <Shell>
        <Box sx={{ display: 'grid', placeItems: 'center', py: 6 }}>
          <CircularProgress aria-label="링크 확인 중" />
        </Box>
      </Shell>
    );
  }

  // 8~64자 + 두 값 일치 시 저장 가능 (문자 조합 규칙은 미확정 — 서버와 같음)
  const longEnough = password.length >= MIN_LENGTH && password.length <= MAX_LENGTH;
  const matches = password.length > 0 && password === confirm;
  const canSave = longEnough && matches && !saving;

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!canSave || !link) return;
    setSaving(true);
    setServerError(null);
    try {
      await setupPassword(link, password);
      setDone(true);
    } catch (err) {
      if (err instanceof ApiError && err.status === 410) setExpired(true);
      else if (err instanceof ApiError && err.status === 400) setServerError(err.fields.password ?? err.message);
      else toast(errorMessage(err), 'error');
    } finally {
      setSaving(false);
    }
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
              onChange={(e) => {
                setPassword(e.target.value);
                setServerError(null);
              }}
              slotProps={{ htmlInput: { maxLength: MAX_LENGTH } }}
              error={(password.length > 0 && !longEnough) || serverError !== null}
              helperText={serverError ?? (password.length > 0 && !longEnough ? '8자 이상 64자 이하로 입력해주세요.' : undefined)}
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
          <Button variant="contained" fullWidth sx={{ mt: 3 }} onClick={() => navigate('/login', { replace: true })}>
            확인
          </Button>
        </DialogContent>
      </Dialog>
    </Shell>
  );
}
