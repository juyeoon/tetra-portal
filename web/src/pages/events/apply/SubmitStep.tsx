import { useState } from 'react';
import { Alert, Box, Button, Card, Dialog, DialogActions, DialogContent, DialogTitle, Link, Stack, Typography } from '@mui/material';
import ArrowBackRoundedIcon from '@mui/icons-material/ArrowBackRounded';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import InfoList from '@/components/common/InfoList';
import SectionTitle from '@/components/common/SectionTitle';
import ScreenPreview, { screenSummary } from '@/components/event/ScreenPreview';
import { radius, text, tones, type } from '@/theme/theme';
import type { Plan } from '@/types';
import { planLabel } from '@/utils/event';
import { formatNumber, formatPeriod } from '@/utils/format';
import { eventTypeLabel } from '@/utils/status';
import { parseStock, toDisplayDateTime, type ApplyDraft } from './draft';

/** 호스팅 시간 — 시간 단위 올림 (서버의 비용 산정과 같은 기준) */
function hostingHours(start: string, end: string) {
  const ms = new Date(end).getTime() - new Date(start).getTime();
  return ms > 0 ? Math.ceil(ms / 3_600_000) : 0;
}

interface Props {
  draft: ApplyDraft;
  plans?: Plan[];
  submitting: boolean;
  /** 서버가 거절한 입력 (400) */
  serverErrors: string[];
  onPrev: () => void;
  /** 이벤트 정보 단계로 — 서버 오류를 고칠 때 */
  onEdit: () => void;
  onSubmit: () => void;
}

/** UI-TP-10 이벤트 신청 - Tab4 (신청서 제출) */
export default function SubmitStep({ draft, plans, submitting, serverErrors, onPrev, onEdit, onSubmit }: Props) {
  const [previewOpen, setPreviewOpen] = useState(false);
  const hostingStart = toDisplayDateTime(draft.hostingStart);
  const hostingEnd = toDisplayDateTime(draft.hostingEnd);
  const hours = hostingHours(draft.hostingStart, draft.hostingEnd);
  const stock = parseStock(draft.stock);

  // ① 신청 요약 — Tab1~3 입력값
  const summary = [
    { label: '이벤트 타입', value: draft.type ? eventTypeLabel[draft.type] : '-' },
    { label: '이벤트명', value: draft.name },
    { label: '이벤트 기간', value: formatPeriod(toDisplayDateTime(draft.eventStart), toDisplayDateTime(draft.eventEnd)) },
    { label: '호스팅 기간', value: `${formatPeriod(hostingStart, hostingEnd)} (${formatNumber(hours)}시간)` },
    { label: '목표 동시 접속 규모', value: draft.plan ? planLabel(draft.plan, plans) : '-' },
    { label: '이벤트 대표 배너', value: draft.banner?.name ?? '-' },
    { label: '이벤트 엔드포인트 URL', value: <Box sx={{ ...type.monoSm, wordBreak: 'break-all' }}>{draft.endpointUrl}</Box> },
    ...(draft.type === 'COUPON' ? [{ label: '발급 쿠폰', value: draft.coupons.map((c) => c.name.trim()).join(', ') + ` (${draft.coupons.length}종)` }] : []),
    { label: '총 발급 재고', value: `${formatNumber(stock)}매` },
    { label: '이벤트 화면 설정', value: screenSummary(draft.screen) },
  ];

  return (
    <>
      <Stack spacing={3}>
        {serverErrors.length > 0 && (
          <Alert severity="error">
            신청 내용을 확인해주세요.
            <Box component="ul" sx={{ m: 0, mt: 0.5, pl: 2.5 }}>
              {serverErrors.map((m) => (
                <li key={m}>{m}</li>
              ))}
            </Box>
            <Link component="button" type="button" onClick={onEdit} sx={{ mt: 0.5 }}>
              이벤트 정보 수정하기
            </Link>
          </Alert>
        )}
        <Card sx={{ p: 3 }}>
          <SectionTitle
            action={
              <Button size="small" variant="outlined" startIcon={<VisibilityOutlinedIcon />} onClick={() => setPreviewOpen(true)}>
                이벤트 미리보기
              </Button>
            }
          >
            신청 요약
          </SectionTitle>
          <InfoList items={summary} labelWidth="wide" />
        </Card>

        {/* ③ 공지 및 주의사항 */}
        <Box sx={{ p: 3, borderRadius: radius.xl, bgcolor: tones.amber.bg }}>
          <Typography variant="h4" component="h3" sx={{ color: tones.amber.fg }}>
            공지 및 주의사항
          </Typography>
          <Stack component="ul" spacing={0.5} sx={{ m: 0, mt: 1.5, pl: 2.5, color: tones.amber.fg }}>
            <Typography component="li" variant="body1">
              신청 후 최소 3영업일 내 검수가 진행되며, 승인 전까지 이벤트 페이지는 노출되지 않습니다.
            </Typography>
            <Typography component="li" variant="body1">
              신청 직후에는 "신청됨(처리 중)"으로 표시되고, 배너 업로드가 끝나면 "승인 대기 중"으로 바뀝니다.
            </Typography>
            <Typography component="li" variant="body1">
              목표 동시 접속 규모를 초과하는 트래픽이 발생하면 자동으로 대기열이 적용됩니다.
            </Typography>
            <Typography component="li" variant="body1">
              추가 트래픽이 예상되는 경우 사전에 문의해주세요.
            </Typography>
          </Stack>
        </Box>
      </Stack>

      {/* ④ 이전 단계로 · ⑤ 신청하기 */}
      <Stack direction="row" justifyContent="space-between" sx={{ mt: 4 }}>
        <Button variant="outlined" size="large" startIcon={<ArrowBackRoundedIcon />} onClick={onPrev} disabled={submitting}>
          이전 단계로
        </Button>
        <Button variant="contained" size="large" onClick={onSubmit} disabled={submitting || serverErrors.length > 0} sx={{ minWidth: 160 }}>
          {submitting ? '신청 중…' : '신청하기'}
        </Button>
      </Stack>

      <Dialog open={previewOpen} onClose={() => setPreviewOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle>이벤트 미리보기</DialogTitle>
        <DialogContent>
          <ScreenPreview screen={draft.screen} height={480} />
          <Typography variant="body2" sx={{ mt: 1.5, color: text.tertiary, textAlign: 'center' }}>
            위치와 테마만 반영한 예시입니다. 실제 이벤트 화면은 승인 후 이 구성으로 생성됩니다.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button variant="contained" onClick={() => setPreviewOpen(false)}>
            닫기
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
