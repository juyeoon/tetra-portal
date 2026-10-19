import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Box, Button, Card, Stack, Switch, Typography } from '@mui/material';
import LogoutOutlinedIcon from '@mui/icons-material/LogoutOutlined';
import { errorMessage } from '@/api/client';
import { getSettings, setEmailEnabled } from '@/api/portal';
import { useApi } from '@/api/useApi';
import InfoList from '@/components/common/InfoList';
import SectionTitle from '@/components/common/SectionTitle';
import StatusChip from '@/components/common/StatusChip';
import PageHeader from '@/components/layout/PageHeader';
import { useAccount, useAuth } from '@/store/AuthContext';
import { useToast } from '@/store/ToastContext';
import { color, font, line, radius, size, text, tones } from '@/theme/theme';

/** UI-TP-14 설정 — 계정 정보 · 이메일 수신 설정 · Slack 연동(MVP 1차 준비 중) · 로그아웃 */
export default function SettingsPage() {
  const navigate = useNavigate();
  const { logout } = useAuth();
  const toast = useToast();
  const account = useAccount();
  const settings = useApi(getSettings, []);
  const [savingEmail, setSavingEmail] = useState(false);
  const emailEnabled = settings.data?.emailEnabled ?? false;

  const toggleEmail = async (enabled: boolean) => {
    setSavingEmail(true);
    try {
      const saved = await setEmailEnabled(enabled);
      settings.setData(() => saved);
      toast(saved.emailEnabled ? '이메일 수신을 켰습니다.' : '이메일 수신을 껐습니다.', 'info');
    } catch (e) {
      toast(errorMessage(e), 'error');
    } finally {
      setSavingEmail(false);
    }
  };

  return (
    <>
      <PageHeader eyebrow="SETTINGS" breadcrumbs={['설정']} />

      <Stack spacing={3} sx={{ maxWidth: size.contentForm }}>
        {/* ① 계정 정보 (읽기 전용) */}
        <Card sx={{ p: 3 }}>
          <SectionTitle>계정 정보</SectionTitle>
          <InfoList
            items={[
              { label: '소속 기업', value: account.companyName },
              { label: '이메일 (아이디)', value: account.email },
            ]}
          />
        </Card>

        {/* ③ 이메일 송수신 설정 */}
        <Card sx={{ p: 3 }}>
          <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={2}>
            <Box>
              <Typography variant="h4" component="h3">
                이메일 송수신 설정
              </Typography>
              <Typography variant="body2" sx={{ mt: 0.5, color: text.tertiary }}>
                이벤트 상태 변경, 리포트 생성 등의 알림을 이메일로 받습니다.
              </Typography>
              {settings.error && (
                <Typography variant="body2" sx={{ mt: 0.5, color: tones.red.fg }}>
                  설정을 불러오지 못했습니다. {settings.error.message}{' '}
                  <Box component="button" type="button" onClick={settings.reload} sx={{ p: 0, border: 0, bgcolor: 'transparent', color: 'inherit', textDecoration: 'underline', cursor: 'pointer', font: 'inherit' }}>
                    다시 시도
                  </Box>
                </Typography>
              )}
            </Box>
            <Stack direction="row" alignItems="center" spacing={0.5}>
              <Typography variant="body2" sx={{ color: emailEnabled ? tones.green.fg : text.tertiary, fontWeight: font.weight.semibold }}>
                수신 {settings.data ? (emailEnabled ? 'ON' : 'OFF') : '-'}
              </Typography>
              <Switch
                checked={emailEnabled}
                disabled={!settings.data || savingEmail}
                onChange={(e) => toggleEmail(e.target.checked)}
                slotProps={{ input: { 'aria-label': '이메일 수신' } }}
              />
            </Stack>
          </Stack>
        </Card>

        {/* ④~⑦ Slack 연동 — MVP 1차는 제공하지 않는다(slackAvailable false) */}
        <Card sx={{ p: 3 }}>
          <SectionTitle action={<StatusChip label={settings.data?.slackAvailable ? '미연동' : '준비 중'} tone="grey" />}>Slack 연동</SectionTitle>
          <Box sx={{ p: 2, borderRadius: radius.lg, bgcolor: color.surface, border: `1px dashed ${line.strong}` }}>
            <Typography variant="body1" sx={{ color: text.secondary }}>
              Slack 연동(워크스페이스 · 우선순위별 채널 등록)은 준비 중입니다.
            </Typography>
            <Typography variant="body2" sx={{ mt: 0.5, color: text.tertiary }}>
              그동안 알림은 알림 센터와, 위에서 켠 경우 로그인 계정 이메일로 받을 수 있습니다.
            </Typography>
          </Box>
        </Card>

        {/* ⑧ 로그아웃 */}
        <Box>
          <Button
            variant="outlined"
            color="error"
            size="large"
            startIcon={<LogoutOutlinedIcon />}
            onClick={async () => {
              await logout();
              navigate('/login', { replace: true });
            }}
          >
            로그아웃
          </Button>
        </Box>
      </Stack>
    </>
  );
}
