import { useNavigate, useParams } from 'react-router-dom';
import { Box, Button, Tab, Tabs } from '@mui/material';
import ApplicationContent from '@/components/event/ApplicationContent';
import NotFoundView from '@/components/common/NotFoundView';
import { useTabParam } from '@/components/common/useTabParam';
import PageHeader from '@/components/layout/PageHeader';
import { useAppData } from '@/store/AppDataContext';
import EventResultPanel from './components/EventResultPanel';
import EventSummary from './components/EventSummary';
import IntegrationGuide from './components/IntegrationGuide';

const TABS = ['application', 'guide', 'result'] as const;
const TAB_LABEL: Record<(typeof TABS)[number], string> = { application: '신청 내용', guide: '사전 가이드', result: '결과' };

/** UI-TP-06 이벤트 내역 - 상세 (신청 내용 / 사전 가이드 UI-TP-6-1 / 결과 UI-TP-6-2) */
export default function EventDetailPage() {
  const navigate = useNavigate();
  const { eventId = '' } = useParams();
  const { getEvent, account } = useAppData();
  const [tab, setTab] = useTabParam(TABS, 'application');
  const event = getEvent(eventId);

  if (!event) {
    return <NotFoundView eyebrow="EVENTS" breadcrumbs={['이벤트 내역', '상세']} back={{ to: '/events', label: '이벤트 내역' }} message="이벤트를 찾을 수 없습니다" />;
  }

  return (
    <>
      <PageHeader eyebrow="EVENTS" breadcrumbs={['이벤트 내역', '상세']} />

      <EventSummary event={event} />

      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mt: 3, mb: 3 }} aria-label="이벤트 상세 탭">
        {TABS.map((t) => (
          <Tab key={t} value={t} label={TAB_LABEL[t]} />
        ))}
      </Tabs>

      {tab === 'application' && <ApplicationContent event={event} tenant={{ name: account.companyName, id: account.tenantId }} />}
      {tab === 'guide' && <IntegrationGuide event={event} />}
      {tab === 'result' && <EventResultPanel event={event} />}

      <Box sx={{ mt: 5, display: 'flex', justifyContent: 'center' }}>
        <Button variant="outlined" size="large" onClick={() => navigate('/events')} sx={{ minWidth: 200 }}>
          목록보기
        </Button>
      </Box>
    </>
  );
}
