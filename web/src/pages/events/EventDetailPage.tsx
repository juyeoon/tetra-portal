import { useNavigate, useParams } from 'react-router-dom';
import { Box, Button, Tab, Tabs, Tooltip } from '@mui/material';
import { getEvent, listPlans } from '@/api/portal';
import { useApi } from '@/api/useApi';
import ApplicationContent from '@/components/event/ApplicationContent';
import { ErrorView, LoadingView } from '@/components/common/LoadState';
import NotFoundView from '@/components/common/NotFoundView';
import { useTabParam } from '@/components/common/useTabParam';
import PageHeader from '@/components/layout/PageHeader';
import { useAccount } from '@/store/AuthContext';
import { CHECKING_NOTE, eventChipsOf } from '@/utils/status';
import EventResultPanel from './components/EventResultPanel';
import EventSummary from './components/EventSummary';
import IntegrationGuide from './components/IntegrationGuide';

const TABS = ['application', 'guide', 'result'] as const;
const TAB_LABEL: Record<(typeof TABS)[number], string> = { application: '신청 내용', guide: '사전 가이드', result: '결과' };

const notFound = (
  <NotFoundView eyebrow="EVENTS" breadcrumbs={['이벤트 내역', '상세']} back={{ to: '/events', label: '이벤트 내역' }} message="이벤트를 찾을 수 없습니다" />
);

/** UI-TP-06 이벤트 내역 - 상세 (신청 내용 / 사전 가이드 UI-TP-6-1 / 결과 UI-TP-6-2) */
export default function EventDetailPage() {
  const navigate = useNavigate();
  const { eventId: raw = '' } = useParams();
  const eventId = /^\d{1,10}$/.test(raw) ? Number(raw) : null;
  const account = useAccount();
  const [tab, setTab] = useTabParam(TABS, 'application');
  const detail = useApi(() => (eventId === null ? Promise.reject() : getEvent(eventId)), [eventId]);
  const plans = useApi(listPlans, []);
  const event = detail.data;

  if (eventId === null || detail.error?.status === 404) return notFound;
  if (detail.error) {
    return (
      <>
        <PageHeader eyebrow="EVENTS" breadcrumbs={['이벤트 내역', '상세']} />
        <ErrorView message={detail.error.message} onRetry={detail.reload} />
      </>
    );
  }
  if (!event) {
    return (
      <>
        <PageHeader eyebrow="EVENTS" breadcrumbs={['이벤트 내역', '상세']} />
        <LoadingView />
      </>
    );
  }

  // 진행 전 · 진행 중(승인 전 포함)이면 결과 탭을 막는다 — 주소로 들어와도 신청 내용으로
  const resultLocked = event.status.resultState === 'IN_PROGRESS';
  const current = tab === 'result' && resultLocked ? 'application' : tab;
  // 실패했거나 예정 시각이 지났는데 진행되지 않았으면 확인 중 — 상단 칩 · 결과 탭 툴팁 · 사전 가이드 도메인 안내
  const chips = eventChipsOf(event.status, event, Date.now());

  return (
    <>
      <PageHeader eyebrow="EVENTS" breadcrumbs={['이벤트 내역', '상세']} />

      <EventSummary event={event} chips={chips} />

      <Tabs value={current} onChange={(_, v) => setTab(v)} sx={{ mt: 3, mb: 3 }} aria-label="이벤트 상세 탭">
        {TABS.map((t) =>
          t === 'result' && resultLocked ? (
            <Tooltip key={t} title={chips.checking ? CHECKING_NOTE : '이벤트가 끝나고 집계가 시작되면 결과를 볼 수 있습니다.'} arrow>
              <span>
                <Tab value={t} label={TAB_LABEL[t]} disabled />
              </span>
            </Tooltip>
          ) : (
            <Tab key={t} value={t} label={TAB_LABEL[t]} />
          ),
        )}
      </Tabs>

      {current === 'application' && <ApplicationContent event={event} plans={plans.data} tenant={{ name: account.companyName, id: account.tenantId }} />}
      {current === 'guide' && <IntegrationGuide event={event} tenantId={account.tenantId} checking={chips.checking} />}
      {current === 'result' && <EventResultPanel eventId={event.eventId} />}

      <Box sx={{ mt: 5, display: 'flex', justifyContent: 'center' }}>
        <Button variant="outlined" size="large" onClick={() => navigate('/events')} sx={{ minWidth: 200 }}>
          목록보기
        </Button>
      </Box>
    </>
  );
}
