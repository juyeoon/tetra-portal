import type { ReactNode } from 'react';
import { Box, Card, Stack, Typography } from '@mui/material';
import ImageOutlinedIcon from '@mui/icons-material/ImageOutlined';
import InfoList from '@/components/common/InfoList';
import SectionTitle from '@/components/common/SectionTitle';
import { color, line, radius, size, text, type } from '@/theme/theme';
import type { ApplicationStatus, EventDetail, Plan } from '@/types';
import { planLabel } from '@/utils/event';
import { formatDateTime, formatNumber, formatPeriod } from '@/utils/format';
import { eventTypeLabel } from '@/utils/status';
import CouponTable from './CouponTable';
import { screenSummary } from './ScreenPreview';
import StatusLogTimeline from './StatusLogTimeline';

interface Props {
  event: EventDetail;
  /** 요금제 수용 인원 표시용 — 없으면 코드만 */
  plans?: Plan[];
  /** 신청자 정보의 테넌트 표시 */
  tenant: { name: string; id: string };
}

/** 배너는 신청 뒤 비동기로 올라간다 — 신청 상태로 결과를 안내한다 (파일 이름 · 크기는 저장하지 않음) */
function bannerText(status: ApplicationStatus) {
  if (status === 'APPLYING') return '업로드 확인 중';
  if (status === 'APPLY_FAILED') return '등록되지 않음 — 상태 처리 기록의 원인을 확인하세요';
  return '등록 완료';
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
export default function ApplicationContent({ event, plans, tenant }: Props) {
  const basic = [
    { label: '이벤트명', value: event.name },
    { label: '이벤트 타입', value: eventTypeLabel[event.eventType] },
    { label: '이벤트 기간', value: formatPeriod(formatDateTime(event.startAt), formatDateTime(event.endAt)) },
    { label: '호스팅 기간', value: formatPeriod(formatDateTime(event.hostingStartAt), formatDateTime(event.hostingEndAt)) },
    {
      label: '이벤트 대표 배너',
      value: (
        <Stack direction="row" spacing={1.5} alignItems="center">
          {/* 배너 이미지 주소는 상세 응답에 없다 — 썸네일 대신 자리 표시 */}
          <Box sx={{ width: 96, height: 34, flexShrink: 0, display: 'grid', placeItems: 'center', borderRadius: radius.sm, border: `1px dashed ${line.strong}`, color: text.tertiary }}>
            <ImageOutlinedIcon sx={{ fontSize: size.iconSm }} />
          </Box>
          <Box sx={{ minWidth: 0 }}>
            {bannerText(event.status.applicationStatus)}
            <Typography variant="body2" sx={{ color: text.tertiary }}>
              대기열·이벤트 화면 상단에 노출
            </Typography>
          </Box>
        </Stack>
      ),
    },
    { label: '총 발급 재고', value: `${formatNumber(event.stockCount)}매` },
    { label: '이벤트 화면 설정', value: screenSummary(event.layout) },
  ];

  const traffic = [
    { label: '목표 동시 접속 규모', value: <WithNote value={planLabel(event.trafficPlanCode, plans)} note="플랜 수용 인원 기준으로 이벤트 호스팅 리소스가 산정됩니다." /> },
    { label: '이벤트 엔드포인트 URL', value: <WithNote value={<Box sx={{ ...type.monoSm, wordBreak: 'break-all' }}>{event.endpointUrl}</Box>} note="대기열을 통과한 고객이 이동하는 고객사 페이지" /> },
  ];

  const applicant = [
    { label: '신청자', value: `${event.applicantName} (테넌트 관리자)` },
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
    { label: '이벤트 신청 날짜', value: formatDateTime(event.appliedAt) },
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
        {event.eventType === 'COUPON' && event.coupons.length > 0 && (
          <Card sx={{ p: 3 }}>
            <SectionTitle>발급 쿠폰 내역</SectionTitle>
            <CouponTable coupons={event.coupons} stockCount={event.stockCount} />
          </Card>
        )}
      </Stack>

      <Stack spacing={3} sx={{ minWidth: 0 }}>
        <Card sx={{ p: 3 }}>
          <SectionTitle>상태 처리 기록</SectionTitle>
          <StatusLogTimeline logs={event.logs} applicantName={event.applicantName} />
        </Card>
        <Card sx={{ p: 3 }}>
          <SectionTitle>신청자 정보</SectionTitle>
          <InfoList items={applicant} />
        </Card>
      </Stack>
    </Box>
  );
}
