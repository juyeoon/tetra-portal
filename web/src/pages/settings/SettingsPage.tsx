import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Box, Button, Card, Dialog, DialogActions, DialogContent, DialogTitle, Stack, Switch, TextField, Typography } from '@mui/material';
import LogoutOutlinedIcon from '@mui/icons-material/LogoutOutlined';
import InfoList from '@/components/common/InfoList';
import SectionTitle from '@/components/common/SectionTitle';
import StatusChip from '@/components/common/StatusChip';
import PageHeader from '@/components/layout/PageHeader';
import { useAppData } from '@/store/AppDataContext';
import { useToast } from '@/store/ToastContext';
import { color, font, line, radius, size, text, tones } from '@/theme/theme';
import type { NotificationPriority } from '@/types';
import { priorityMeta } from '@/utils/status';
import { isSlackChannel } from '@/utils/validation';

const PRIORITIES: NotificationPriority[] = ['CRITICAL', 'WARNING', 'INFO'];

/** UI-TP-14 설정 — 계정 정보 · 이메일 수신 설정 · Slack 연동 · 로그아웃 */
export default function SettingsPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const { account, settings, setEmailEnabled, disconnectSlack, connectSlack, saveSlack } = useAppData();
  const { slack } = settings;

  const [workspace, setWorkspace] = useState(slack.workspace);
  const [channels, setChannels] = useState(slack.channels);
  const [saving, setSaving] = useState(false);
  const [connecting, setConnecting] = useState(false);
  const [confirmDisconnect, setConfirmDisconnect] = useState(false);

  useEffect(() => {
    setWorkspace(slack.workspace);
    setChannels(slack.channels);
  }, [slack.workspace, slack.channels]);

  const channelErrors = PRIORITIES.filter((p) => channels[p].trim() && !isSlackChannel(channels[p]));
  const dirty = workspace !== slack.workspace || PRIORITIES.some((p) => channels[p] !== slack.channels[p]);
  const canSave = dirty && channelErrors.length === 0 && !saving;

  const save = async () => {
    setSaving(true);
    await saveSlack({ workspace: workspace.trim(), channels: { CRITICAL: channels.CRITICAL.trim(), WARNING: channels.WARNING.trim(), INFO: channels.INFO.trim() } });
    setSaving(false);
    toast('채널을 저장했습니다. 알림 센터에 바로 반영됩니다.');
  };

  const connect = async () => {
    setConnecting(true);
    await connectSlack();
    setConnecting(false);
    toast('Slack이 연동되었습니다.');
  };

  const disconnect = async () => {
    setConfirmDisconnect(false);
    await disconnectSlack();
    toast('Slack 연동을 해제했습니다.', 'info');
  };

  const toggleEmail = (enabled: boolean) => {
    setEmailEnabled(enabled);
    toast(enabled ? '이메일 수신을 켰습니다.' : '이메일 수신을 껐습니다.', 'info');
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
            </Box>
            <Stack direction="row" alignItems="center" spacing={0.5}>
              <Typography variant="body2" sx={{ color: settings.emailEnabled ? tones.green.fg : text.tertiary, fontWeight: font.weight.semibold }}>
                수신 {settings.emailEnabled ? 'ON' : 'OFF'}
              </Typography>
              <Switch checked={settings.emailEnabled} onChange={(e) => toggleEmail(e.target.checked)} slotProps={{ input: { 'aria-label': '이메일 수신' } }} />
            </Stack>
          </Stack>
        </Card>

        {/* ④~⑦ Slack 연동 */}
        <Card sx={{ p: 3 }}>
          <SectionTitle action={slack.connected ? <StatusChip label="연동됨" tone="green" /> : <StatusChip label="미연동" tone="grey" />}>Slack 연동</SectionTitle>

          {slack.connected ? (
            <Stack direction={{ xs: 'column', sm: 'row' }} alignItems={{ sm: 'center' }} justifyContent="space-between" spacing={1.5} sx={{ p: 2, borderRadius: radius.lg, bgcolor: color.surface, border: `1px solid ${line.soft}` }}>
              <Box sx={{ minWidth: 0 }}>
                <Typography variant="body2" sx={{ color: text.tertiary }}>
                  연동된 워크스페이스 · 계정
                </Typography>
                <Typography variant="subtitle1" sx={{ wordBreak: 'break-all' }}>
                  {slack.connectedLabel}
                </Typography>
              </Box>
              <Button size="small" variant="outlined" color="error" onClick={() => setConfirmDisconnect(true)} sx={{ flexShrink: 0 }}>
                연동 해제
              </Button>
            </Stack>
          ) : (
            <Stack direction={{ xs: 'column', sm: 'row' }} alignItems={{ sm: 'center' }} justifyContent="space-between" spacing={1.5} sx={{ p: 2, borderRadius: radius.lg, bgcolor: color.surface, border: `1px dashed ${line.strong}` }}>
              <Typography variant="body1" sx={{ color: text.secondary }}>
                Slack을 연동하면 우선순위별 채널로 알림을 받을 수 있습니다.
              </Typography>
              <Button variant="contained" onClick={connect} disabled={connecting} sx={{ flexShrink: 0 }}>
                {connecting ? '연동 중…' : 'Slack 연동하기'}
              </Button>
            </Stack>
          )}

          <Stack spacing={2.5} sx={{ mt: 3 }}>
            {/* ⑤ 워크스페이스 등록 — 연동 여부와 관계없이 항상 노출 */}
            <Box>
              <Typography component="label" htmlFor="slack-workspace" variant="body1" sx={{ display: 'block', mb: 1, fontWeight: font.weight.medium }}>
                워크스페이스 등록{' '}
                <Typography component="span" variant="body2" sx={{ color: text.tertiary }}>
                  (최대 1개)
                </Typography>
              </Typography>
              <TextField id="slack-workspace" fullWidth placeholder="예: TenantCompany" value={workspace} onChange={(e) => setWorkspace(e.target.value)} />
            </Box>

            {/* ⑥ 채널명 등록 — 긴급·경고·정보 각 1개 */}
            <Box>
              <Typography variant="body1" sx={{ mb: 1, fontWeight: font.weight.medium }}>
                채널명 등록{' '}
                <Typography component="span" variant="body2" sx={{ color: text.tertiary }}>
                  (긴급 · 경고 · 정보 각 1개)
                </Typography>
              </Typography>
              <Stack spacing={1}>
                {PRIORITIES.map((p) => {
                  const meta = priorityMeta[p];
                  const invalid = channelErrors.includes(p);
                  return (
                    <Stack key={p} direction="row" alignItems="flex-start" spacing={1.5}>
                      <Box sx={{ width: 56, flexShrink: 0, pt: 1 }}>
                        <StatusChip label={meta.label} tone={meta.tone} />
                      </Box>
                      <TextField
                        fullWidth
                        size="small"
                        placeholder={`#tenant-company-${p.toLowerCase()}`}
                        value={channels[p]}
                        onChange={(e) => setChannels((prev) => ({ ...prev, [p]: e.target.value }))}
                        error={invalid}
                        helperText={invalid ? '#으로 시작하는 소문자·숫자·-·_ 채널명을 입력해주세요.' : undefined}
                        slotProps={{ htmlInput: { 'aria-label': `${meta.label} 채널명` } }}
                      />
                    </Stack>
                  );
                })}
              </Stack>
              <Typography variant="body2" sx={{ mt: 1.5, color: text.tertiary }}>
                ※ 여기서 등록한 워크스페이스·채널은 알림 센터의 "Slack 연동 · 우선순위별 채널"에 그대로 반영됩니다.
              </Typography>
            </Box>

            <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
              <Button variant="contained" onClick={save} disabled={!canSave}>
                {saving ? '저장 중…' : '채널 저장'}
              </Button>
            </Box>
          </Stack>
        </Card>

        {/* ⑧ 로그아웃 */}
        <Box>
          {/* TODO: 로그아웃 API 연동 */}
          <Button variant="outlined" color="error" size="large" startIcon={<LogoutOutlinedIcon />} onClick={() => navigate('/login')}>
            로그아웃
          </Button>
        </Box>
      </Stack>

      <Dialog open={confirmDisconnect} onClose={() => setConfirmDisconnect(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Slack 연동을 해제할까요?</DialogTitle>
        <DialogContent>
          <Typography variant="body1" sx={{ color: text.secondary }}>
            해제하면 Slack 채널로 알림이 전송되지 않습니다. 등록한 워크스페이스·채널명은 그대로 남습니다.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button variant="outlined" onClick={() => setConfirmDisconnect(false)}>
            취소
          </Button>
          <Button variant="contained" color="error" onClick={disconnect}>
            연동 해제
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
