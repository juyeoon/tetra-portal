import { Link as RouterLink } from 'react-router-dom';
import { Link } from '@mui/material';
import { errorMessage } from '@/api/client';
import { getSettings, listNotifications, markNotificationRead } from '@/api/portal';
import { useApi } from '@/api/useApi';
import { ErrorView, LoadingView } from '@/components/common/LoadState';
import NotificationCenter from '@/components/notification/NotificationCenter';
import PageHeader from '@/components/layout/PageHeader';
import { useToast } from '@/store/ToastContext';
import { type } from '@/theme/theme';

/** UI-TP-13 알림 센터 */
export default function NotificationCenterPage() {
  const toast = useToast();
  const { data: notifications, error, reload, setData } = useApi(listNotifications, []);
  const settings = useApi(getSettings, []);

  // 화면을 먼저 바꾸고, 실패하면 되돌린다
  const read = async (id: number) => {
    const mark = (value: boolean) => setData((prev) => prev?.map((n) => (n.notificationId === id ? { ...n, read: value } : n)));
    mark(true);
    try {
      await markNotificationRead(id);
    } catch (e) {
      mark(false);
      toast(errorMessage(e), 'error');
    }
  };

  return (
    <>
      <PageHeader eyebrow="NOTIFICATIONS" breadcrumbs={['알림 센터']} />
      {error ? (
        <ErrorView message={error.message} onRetry={reload} />
      ) : !notifications ? (
        <LoadingView />
      ) : (
        <NotificationCenter
          notifications={notifications}
          slackAvailable={settings.data?.slackAvailable ?? false}
          onRead={read}
          settingsAction={
            <Link component={RouterLink} to="/settings" sx={{ ...type.caption, whiteSpace: 'nowrap' }}>
              설정 변경 →
            </Link>
          }
        />
      )}
    </>
  );
}
