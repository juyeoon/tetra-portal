import { useState, type FormEvent, type ReactNode } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { Box, Button, IconButton, InputAdornment, Link, Stack, TextField, Typography } from '@mui/material';
import BlockRoundedIcon from '@mui/icons-material/BlockRounded';
import VisibilityOffOutlinedIcon from '@mui/icons-material/VisibilityOffOutlined';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import logoWhite from '@/assets/logo-white.png';
import wavePattern from '@/assets/wave-pattern.png';
import { APP_VERSION } from '@/mocks/account';
import { errorMessage } from '@/api/client';
import { useAuth } from '@/store/AuthContext';
import { useToast } from '@/store/ToastContext';
import { color, font, onNavy, radius, size, text, tones, type } from '@/theme/theme';
import SupportMailDialog from './components/SupportMailDialog';

/** 입력칸 안 왼쪽 라벨 (시안: "이메일 | 입력값") */
function InlineLabel({ children }: { children: ReactNode }) {
  return (
    <InputAdornment position="start" sx={{ width: 64, mr: 0 }}>
      <Typography variant="body2" sx={{ color: text.tertiary }}>
        {children}
      </Typography>
    </InputAdornment>
  );
}

/** UI-TP-01 로그인 · UI-TP-02 로그인 실패(같은 페이지 하단 배너). 회원가입 기능 없음 */
export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { status, login } = useAuth();
  const toast = useToast();
  /** 로그인 화면으로 보내지기 전 주소 (RequireAuth) */
  const from = (location.state as { from?: string } | null)?.from ?? '/events';
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPw, setShowPw] = useState(false);
  const [failed, setFailed] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [supportOpen, setSupportOpen] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!email.trim() || !password || submitting) return;
    setSubmitting(true);
    try {
      if (await login(email, password)) navigate(from, { replace: true });
      else setFailed(true);
    } catch (err) {
      toast(errorMessage(err), 'error');
    } finally {
      setSubmitting(false);
    }
  };

  // 이미 로그인돼 있으면(쿠키가 살아 있음) 바로 포털로
  if (status === 'authenticated') return <Navigate to={from} replace />;

  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'minmax(420px, 48%) 1fr' } }}>
      {/* 좌측 패널 — 서비스명 · 버전 및 라이선스 */}
      <Box
        sx={{
          position: 'relative',
          overflow: 'hidden',
          bgcolor: color.navy,
          color: onNavy.primary,
          px: { xs: 3, md: 5 },
          py: { xs: 4, md: 5 },
          display: 'flex',
          flexDirection: 'column',
          minHeight: { xs: 280, md: 'auto' },
        }}
      >
        <Box
          aria-hidden
          sx={{
            position: 'absolute',
            inset: '-10% -25% -10% -15%',
            backgroundImage: `url(${wavePattern})`,
            backgroundSize: 'cover',
            backgroundPosition: 'center',
            opacity: 0.32,
            pointerEvents: 'none',
          }}
        />

        <Box sx={{ position: 'relative', my: 'auto', py: 4, px: { md: 3 } }}>
          <Stack direction="row" alignItems="flex-end" spacing={1.5}>
            <Box component="img" src={logoWhite} alt="TETRA" sx={{ height: { xs: 44, md: 68 }, display: 'block' }} />
            <Typography component="span" sx={{ fontFamily: font.heading, fontWeight: font.headingWeight.title, fontSize: { xs: 22, md: 34 }, lineHeight: 1, letterSpacing: font.letterSpacing.heading, color: onNavy.primary }}>
              PORTAL
            </Typography>
          </Stack>
          <Typography variant="h3" component="h2" sx={{ mt: 1.5, color: onNavy.primary }}>
            이벤트 트래픽 관리 시스템
          </Typography>
        </Box>

        <Box sx={{ position: 'relative', display: { xs: 'none', md: 'block' } }}>
          <Typography variant="subtitle2" sx={{ color: onNavy.primary }}>
            버전 및 라이선스
          </Typography>
          <Typography variant="body2" sx={{ mt: 1, color: onNavy.secondary }}>
            {APP_VERSION} · 본 소프트웨어는 Tetra Operation의 자산이며 무단 복제 및 배포를 금합니다.
          </Typography>
          <Typography variant="body2" sx={{ mt: 0.5, color: onNavy.tertiary }}>
            © 2026 Tetra Operation. All rights reserved.
          </Typography>
        </Box>
      </Box>

      {/* 우측 로그인 폼 */}
      <Box sx={{ display: 'grid', placeItems: 'center', px: { xs: 3, sm: 6 }, py: 6 }}>
        <Box sx={{ width: '100%', maxWidth: size.contentNarrow }}>
          <Box component="form" onSubmit={handleSubmit} noValidate>
            <Typography variant="h2" component="h1" sx={{ mb: 3 }}>
              관리자 로그인
            </Typography>

            <Stack spacing={1.5}>
              <TextField
                id="login-email"
                fullWidth
                type="email"
                autoComplete="username"
                value={email}
                onChange={(e) => {
                  setEmail(e.target.value);
                  setFailed(false);
                }}
                slotProps={{ input: { startAdornment: <InlineLabel>이메일</InlineLabel> }, htmlInput: { 'aria-label': '이메일' } }}
              />
              <TextField
                id="login-password"
                fullWidth
                type={showPw ? 'text' : 'password'}
                autoComplete="current-password"
                value={password}
                onChange={(e) => {
                  setPassword(e.target.value);
                  setFailed(false);
                }}
                slotProps={{
                  input: {
                    startAdornment: <InlineLabel>비밀번호</InlineLabel>,
                    endAdornment: (
                      <InputAdornment position="end">
                        <IconButton size="small" onClick={() => setShowPw((v) => !v)} edge="end" aria-label="비밀번호 보기 전환">
                          {showPw ? <VisibilityOffOutlinedIcon sx={{ fontSize: size.iconMd }} /> : <VisibilityOutlinedIcon sx={{ fontSize: size.iconMd }} />}
                        </IconButton>
                      </InputAdornment>
                    ),
                  },
                  htmlInput: { 'aria-label': '비밀번호' },
                }}
              />
            </Stack>

            <Button type="submit" variant="contained" size="large" fullWidth disabled={!email.trim() || !password || submitting} sx={{ mt: 2.5 }}>
              로그인
            </Button>
          </Box>

          <Typography variant="body2" sx={{ mt: 2, textAlign: 'center', color: text.tertiary }}>
            비밀번호를 잊으셨나요?{' '}
            <Link component="button" type="button" onClick={() => setSupportOpen(true)} sx={{ ...type.caption, fontWeight: font.weight.semibold, verticalAlign: 'baseline' }}>
              고객센터
            </Link>
          </Typography>

          {/* UI-TP-02 로그인 실패 배너 — 입력 필드·좌측 패널은 그대로 유지 */}
          {failed && (
            <Stack
              role="alert"
              direction="row"
              spacing={1.5}
              sx={{ mt: 4, px: 2, py: 1.75, borderRadius: radius.md, bgcolor: tones.red.bg, borderLeft: `4px solid ${tones.red.dot}` }}
            >
              <BlockRoundedIcon sx={{ fontSize: size.iconMd, color: tones.red.dot, mt: '2px' }} />
              <Box>
                <Typography variant="subtitle1" sx={{ color: tones.red.fg }}>
                  로그인 실패
                </Typography>
                <Typography variant="body1" sx={{ color: text.secondary }}>
                  계정 정보를 찾을 수 없습니다.
                  <br />
                  관리자에게 문의하세요.
                </Typography>
              </Box>
            </Stack>
          )}
        </Box>
      </Box>

      <SupportMailDialog open={supportOpen} onClose={() => setSupportOpen(false)} defaultFrom={email} />
    </Box>
  );
}
