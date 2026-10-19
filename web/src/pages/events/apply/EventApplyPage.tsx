import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError, errorMessage } from '@/api/client';
import { applyEvent, listPlans } from '@/api/portal';
import { useApi } from '@/api/useApi';
import PageHeader from '@/components/layout/PageHeader';
import { useToast } from '@/store/ToastContext';
import type { EventType } from '@/types';
import { describeServerErrors, initialDraft, toApplication, type ApplyDraft } from './draft';
import InfoStep from './InfoStep';
import ScreenStep from './ScreenStep';
import StepProgress from './StepProgress';
import SubmitStep from './SubmitStep';
import TypeStep from './TypeStep';

/** UI-TP-07~10 이벤트 신청 — 이벤트 타입 → 이벤트 정보 → 이벤트 화면 설정 → 신청서 제출 */
export default function EventApplyPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const plans = useApi(listPlans, []);
  const [step, setStep] = useState(0);
  const [draft, setDraft] = useState<ApplyDraft>(initialDraft);
  const [submitting, setSubmitting] = useState(false);
  /** 서버가 돌려준 입력 오류 (400) — 제출 단계에 보여 준다 */
  const [serverErrors, setServerErrors] = useState<string[]>([]);

  const go = (next: number) => {
    setStep(next);
    window.scrollTo(0, 0);
  };
  const patch = (p: Partial<ApplyDraft>) => {
    setDraft((prev) => ({ ...prev, ...p }));
    setServerErrors([]);
  };

  // 타입을 바꾸면 타입별 입력 항목(쿠폰)이 달라진다
  const selectType = (type: EventType) => {
    patch({ type });
    go(1);
  };

  // 접수(202)되면 상세로 — 배너 업로드가 끝나면 상태가 승인 대기 중(또는 신청 실패)으로 바뀐다
  const submit = async () => {
    if (!draft.banner) return;
    setSubmitting(true);
    setServerErrors([]);
    try {
      const { eventId } = await applyEvent(toApplication(draft), draft.banner);
      toast('이벤트 신청이 접수되었습니다. 배너 확인이 끝나면 승인 대기 중으로 바뀝니다.');
      navigate(`/events/${eventId}`, { replace: true });
    } catch (e) {
      if (e instanceof ApiError && e.status === 400 && Object.keys(e.fields).length > 0) setServerErrors(describeServerErrors(e.fields));
      else toast(errorMessage(e), 'error');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <PageHeader eyebrow="NEW EVENT" breadcrumbs={['이벤트 신청']} />
      <StepProgress current={step} />
      {step === 0 && <TypeStep onSelect={selectType} />}
      {step === 1 && <InfoStep draft={draft} plans={plans.data} plansError={plans.error?.message} onRetryPlans={plans.reload} onChange={patch} onPrev={() => go(0)} onNext={() => go(2)} />}
      {step === 2 && <ScreenStep screen={draft.screen} onChange={(screen) => patch({ screen })} onPrev={() => go(1)} onNext={() => go(3)} />}
      {step === 3 && <SubmitStep draft={draft} plans={plans.data} submitting={submitting} serverErrors={serverErrors} onPrev={() => go(2)} onEdit={() => go(1)} onSubmit={submit} />}
    </>
  );
}
