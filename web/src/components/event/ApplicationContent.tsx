import type { ReactNode } from 'react';
import { Box, Card, Stack, Typography } from '@mui/material';
import ImageOutlinedIcon from '@mui/icons-material/ImageOutlined';
import InfoList from '@/components/common/InfoList';
import SectionTitle from '@/components/common/SectionTitle';
import { color, line, radius, size, text, type } from '@/theme/theme';
import type { PortalEvent } from '@/types';
import { planLabel } from '@/utils/event';
import { formatPeriod } from '@/utils/format';
import { eventTypeLabel } from '@/utils/status';
import CouponTable from './CouponTable';
import StatusLogTimeline from './StatusLogTimeline';

interface Props {
  event: PortalEvent;
  /** 신청자 정보의 테넌트 표시 */
  tenant: { name: string; id: string };
}

/** 값 + 보조 설명 */
function WithNote({ value, note }: { value: ReactNode; note: string }) {
  return (
    <>
      {value}
      <Typography variant="body2" sx={{ mt: 0.25, color: text.tertiary }}>
        {note}
      </Typography>
    </>
  );
}

function StepTitle({ step, title, caption }: { step: number; title: string; caption: string }) {
  return (
    <SectionTitle>
      <Stack direction="row" alignItems="center" spacing={1} component="span">
        <Box component="span" sx={{ width: 22, height: 22, display: 'inline-grid', placeItems: 'center', bgcolor: color.navy, color: color.white, ...type.caption }}>
          {step}
        </Box>
        <span>{title}</span>
        <Typography component="span" variant="body2" sx={{ color: text.tertiary }}>
          {caption}
        </Typography>
      </Stack>
    </SectionTitle>
  );
}

/**
 * 이벤트 상세 "신청 내용" 탭 (UI-TP-06).
 * 콘솔의 신청 내용(OPS-03-2·04-2)에서 재사용할 수 있도록 데이터는 props로만 받는다.
 */
export default function ApplicationContent({ event, tenant }: Props) {
  const basic = [
    { label: '이벤트명', value: event.name },
    { label: '이벤트 타입', value: eventTypeLabel[event.type] },
    { label: '이벤트 기간', value: formatPeriod(event.eventStart, event.eventEnd) },
    {
      label: '이벤트 대표 배너',
      value: (
        <Stack direction="row" spacing={1.5} alignItems="center">
          {/* TODO: 업로드된 배너 이미지 URL이 생기면 썸네일로 교체 */}
          <Box sx={{ width: 96, height: 34, flexShrink: 0, display: 'grid', placeItems: 'center', borderRadius: radius.sm, border: `1px dashed ${line.strong}`, color: text.tertiary }}>
            <ImageOutlinedIcon sx={{ fontSize: size.iconSm }} />
          </Box>
          <Box sx={{ minWidth: 0 }}>
            {event.banner.name}
            <Typography variant="body2" sx={{ color: text.tertiary }}>
              {event.banner.width}×{event.banner.height} · 대기열·이벤트 화면 상단에 노출
            </Typography>
          </Box>
        </Stack>
      ),
    },
  ];

  const traffic = [
    { label: '목표 동시 접속 규모', value: <WithNote value={planLabel(event.plan)} note="플랜 수용 인원 기준으로 이벤트 호스팅 리소스가 산정됩니다." /> },
    {
      label: '추가 트래픽 산정',
      value: <WithNote value={event.extraTraffic ? '포함' : '미포함'} note="포함 선택 시 운영자가 예상 트래픽을 별도로 산정합니다." />,
    },
    { label: '이벤트 엔드포인트 URL', value: <WithNote value={<Box sx={{ ...type.monoSm, wordBreak: 'break-all' }}>{event.endpointUrl}</Box>} note="대기열을 통과한 고객이 이동하는 고객사 페이지" /> },
  ];

  const applicant = [
    { label: '신청자', value: event.applicant },
    {
      label: '테넌트',
      value: (
        <>
          {tenant.name}
          <Typography variant="body2" sx={{ ...type.monoSm, color: text.tertiary }}>
            {tenant.id}
          </Typography>
        </>
      ),
    },
    { label: '이벤트 신청 날짜', value: event.appliedAt },
  ];

  return (
    <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', lg: 'minmax(0, 1.6fr) minmax(0, 1fr)' }, alignItems: 'start' }}>
      <Stack spacing={3} sx={{ minWidth: 0 }}>
        <Card sx={{ p: 3 }}>
          <StepTitle step={1} title="이벤트 기본 정보" caption="이벤트 신청 1단계" />
          <InfoList items={basic} />
        </Card>
        <Card sx={{ p: 3 }}>
          <StepTitle step={2} title="트래픽 및 연동 정보" caption="이벤트 신청 2단계" />
          <InfoList items={traffic} labelWidth="wide" />
        </Card>
        {event.type === 'COUPON' && event.coupons.length > 0 && (
          <Card sx={{ p: 3 }}>
            <SectionTitle>발급 쿠폰 내역</SectionTitle>
            <CouponTable coupons={event.coupons} />
          </Card>
        )}
      </Stack>

      <Stack spacing={3} sx={{ minWidth: 0 }}>
        <Card sx={{ p: 3 }}>
          <SectionTitle>상태 처리 기록</SectionTitle>
          <StatusLogTimeline logs={event.logs} />
        </Card>
        <Card sx={{ p: 3 }}>
          <SectionTitle>신청자 정보</SectionTitle>
          <InfoList items={applicant} />
        </Card>
      </Stack>
    </Box>
  );
}
