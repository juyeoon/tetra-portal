import { Link as RouterLink } from 'react-router-dom';
import { Link } from '@mui/material';
import NotificationCenter from '@/components/notification/NotificationCenter';
import PageHeader from '@/components/layout/PageHeader';
import { useAppData } from '@/store/AppDataContext';
import { type } from '@/theme/theme';

/** UI-TP-13 알림 센터 */
export default function NotificationCenterPage() {
  const { notifications, settings, markNotificationRead } = useAppData();

  return (
    <>
      <PageHeader eyebrow="NOTIFICATIONS" breadcrumbs={['알림 센터']} />
      <NotificationCenter
        notifications={notifications}
        slack={settings.slack}
        onRead={markNotificationRead}
        settingsAction={
          <Link component={RouterLink} to="/settings" sx={{ ...type.caption, whiteSpace: 'nowrap' }}>
            설정 변경 →
          </Link>
        }
      />
    </>
  );
}
