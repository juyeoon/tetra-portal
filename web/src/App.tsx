import { Navigate, Route, Routes } from 'react-router-dom';
import AppLayout from '@/components/layout/AppLayout';
import LoginPage from '@/pages/auth/LoginPage';
import PasswordSetupPage from '@/pages/auth/PasswordSetupPage';
import EventApplyPage from '@/pages/events/apply/EventApplyPage';
import EventDetailPage from '@/pages/events/EventDetailPage';
import EventListPage from '@/pages/events/EventListPage';
import NotificationCenterPage from '@/pages/notifications/NotificationCenterPage';
import ReportPage from '@/pages/reports/ReportPage';
import SettingsPage from '@/pages/settings/SettingsPage';

export default function App() {
  return (
    <Routes>
      {/* 레이아웃 없는 화면 */}
      <Route path="/login" element={<LoginPage />} />
      <Route path="/password/setup" element={<PasswordSetupPage />} />

      {/* 포털 — 모니터링(UI-TP-11)은 기획 단계라 라우트 없음 */}
      <Route element={<AppLayout />}>
        <Route index element={<Navigate to="/events" replace />} />
        <Route path="events">
          <Route index element={<EventListPage />} />
          <Route path="new" element={<EventApplyPage />} />
          <Route path=":eventId" element={<EventDetailPage />} />
        </Route>
        <Route path="reports" element={<ReportPage />} />
        <Route path="notifications" element={<NotificationCenterPage />} />
        <Route path="settings" element={<SettingsPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
