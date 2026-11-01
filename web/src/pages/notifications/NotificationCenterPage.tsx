import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { errorMessage } from '@/api/client';
import { listNotifications, markAllNotificationsRead, markNotificationRead } from '@/api/portal';
import { useApi } from '@/api/useApi';
import { ErrorView, LoadingView } from '@/components/common/LoadState';
import NotificationCenter from '@/components/notification/NotificationCenter';
import {
  listQuery,
  pageToFix,
  paramsFromView,
  readOptimistically,
  viewFromParams,
  withFilters,
  type NotificationView,
} from '@/components/notification/notificationView';
import PageHeader from '@/components/layout/PageHeader';
import { useToast } from '@/store/ToastContext';
import { useUnreadCount } from '@/store/UnreadCountContext';

/** UI-TP-13 알림 센터 — 조건 · 페이지는 주소 쿼리에 둔다(새로고침 · 뒤로 가기에도 그대로, 바꿀 때 기록은 쌓지 않는다) */
export default function NotificationCenterPage() {
  const toast = useToast();
  const [params, setParams] = useSearchParams();
  const view = viewFromParams(params);
  const query = listQuery(view);
  const unread = useUnreadCount();
  // 목록 응답의 unreadCount로 사이드바 배지를 맞춘다(따로 부르지 않는다) — [모두 읽음] 뒤 다시 조회할 때도
  const { data, error, loading, reload, setData } = useApi(
    () =>
      listNotifications(query).then((page) => {
        unread.report(page.unreadCount);
        return page;
      }),
    [query.toString()],
  );
  const [readingAll, setReadingAll] = useState(false);

  // 지금 주소에서 바꾼다 — 검색어가 늦게 반영돼도 그사이 바꾼 다른 조건을 덮지 않게
  const update = (change: (prev: NotificationView) => NotificationView) =>
    setParams((prev) => paramsFromView(change(viewFromParams(prev))), { replace: true });

  // 범위 밖 페이지(마지막 페이지를 다 읽은 뒤 다시 조회 등)면 마지막 페이지로
  useEffect(() => {
    const fix = data ? pageToFix(data) : null;
    if (fix) update((prev) => ({ ...prev, page: fix }));
  }, [data]);

  // 화면을 먼저 바꾸고, 실패하면 되돌리고 토스트 — 이동(링크)은 기다리지도 막지도 않는다. 목록에서 빠지는 건 다음 조회 때, 배지는 성공 직후
  const read = (id: number) =>
    readOptimistically(
      id,
      (change) => setData((prev) => (prev ? { ...prev, items: change(prev.items) } : prev)),
      (notificationId) => markNotificationRead(notificationId).then(unread.refresh),
      (e) => toast(errorMessage(e), 'error'),
    );

  const readAll = async () => {
    setReadingAll(true);
    try {
      await markAllNotificationsRead();
      reload();
    } catch (e) {
      toast(errorMessage(e), 'error');
    } finally {
      setReadingAll(false);
    }
  };

  return (
    <>
      <PageHeader eyebrow="NOTIFICATIONS" breadcrumbs={['알림 센터']} />
      {error ? (
        <ErrorView message={error.message} onRetry={reload} />
      ) : !data ? (
        <LoadingView />
      ) : (
        <NotificationCenter
          data={data}
          filters={view.filters}
          page={view.page}
          loading={loading}
          onFiltersChange={(change) => update((prev) => withFilters(prev, change))}
          onPageChange={(page) => {
            update((prev) => ({ ...prev, page }));
            window.scrollTo(0, 0);
          }}
          onRead={read}
          onReadAll={readAll}
          readingAll={readingAll}
        />
      )}
    </>
  );
}
