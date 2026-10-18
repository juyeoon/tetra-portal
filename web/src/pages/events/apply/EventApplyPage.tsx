import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import PageHeader from '@/components/layout/PageHeader';
import { useAppData } from '@/store/AppDataContext';
import { useToast } from '@/store/ToastContext';
import type { EventType } from '@/types';
import { initialDraft, toEventDraft, type ApplyDraft } from './draft';
import InfoStep from './InfoStep';
import ScreenStep from './ScreenStep';
import StepProgress from './StepProgress';
import SubmitStep from './SubmitStep';
import TypeStep from './TypeStep';

/** UI-TP-07~10 이벤트 신청 — 이벤트 타입 → 이벤트 정보 → 이벤트 화면 설정 → 신청서 제출 */
export default function EventApplyPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const { submitEvent } = useAppData();
  const [step, setStep] = useState(0);
  const [draft, setDraft] = useState<ApplyDraft>(initialDraft);
  const [submitting, setSubmitting] = useState(false);

  const go = (next: number) => {
    setStep(next);
    window.scrollTo(0, 0);
  };
  const patch = (p: Partial<ApplyDraft>) => setDraft((prev) => ({ ...prev, ...p }));

  // 타입을 바꾸면 타입별 입력 항목(쿠폰)이 달라진다
  const selectType = (type: EventType) => {
    patch({ type });
    go(1);
  };

  const submit = async () => {
    setSubmitting(true);
    await submitEvent(toEventDraft(draft));
    setSubmitting(false);
    toast('이벤트 신청이 접수되었습니다.');
    navigate('/events');
  };

  return (
    <>
      <PageHeader eyebrow="NEW EVENT" breadcrumbs={['이벤트 신청']} />
      <StepProgress current={step} />
      {step === 0 && <TypeStep onSelect={selectType} />}
      {step === 1 && <InfoStep draft={draft} onChange={patch} onPrev={() => go(0)} onNext={() => go(2)} />}
      {step === 2 && <ScreenStep screen={draft.screen} onChange={(screen) => patch({ screen })} onPrev={() => go(1)} onNext={() => go(3)} />}
      {step === 3 && <SubmitStep draft={draft} submitting={submitting} onPrev={() => go(2)} onSubmit={submit} />}
    </>
  );
}
