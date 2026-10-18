import { useEffect, useState } from 'react';
import { Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, TextField } from '@mui/material';
import FormField from '@/components/common/FormField';
import { SUPPORT_EMAIL } from '@/mocks/account';
import { useAppData } from '@/store/AppDataContext';
import { useToast } from '@/store/ToastContext';
import { isEmail } from '@/utils/validation';

interface Props {
  open: boolean;
  onClose: () => void;
  /** 로그인 폼에 입력한 이메일 */
  defaultFrom?: string;
}

/** UI-TP-03 고객센터 — 비밀번호 문의용 이메일 작성 모달 */
export default function SupportMailDialog({ open, onClose, defaultFrom = '' }: Props) {
  const { sendSupportMail } = useAppData();
  const toast = useToast();
  const [from, setFrom] = useState('');
  const [subject, setSubject] = useState('');
  const [body, setBody] = useState('');
  const [sending, setSending] = useState(false);
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    if (!open) return;
    setFrom(defaultFrom);
    setSubject('비밀번호 문의');
    setBody('');
    setTouched(false);
  }, [open, defaultFrom]);

  const fromError = touched && !isEmail(from);
  const canSend = isEmail(from) && subject.trim() && body.trim() && !sending;

  const handleSend = async () => {
    setTouched(true);
    if (!canSend) return;
    setSending(true);
    await sendSupportMail({ from: from.trim(), subject: subject.trim(), body: body.trim() });
    setSending(false);
    toast('문의 메일을 보냈습니다. 답변은 입력하신 이메일로 받을 수 있습니다.');
    onClose();
  };

  return (
    <Dialog open={open} onClose={sending ? undefined : onClose} fullWidth maxWidth="sm">
      <DialogTitle>고객센터 문의</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <FormField label="받는 사람" htmlFor="support-to">
            <TextField id="support-to" fullWidth value={SUPPORT_EMAIL} disabled />
          </FormField>
          <FormField label="회신 받을 이메일" htmlFor="support-from" required>
            <TextField
              id="support-from"
              fullWidth
              type="email"
              placeholder="admin@company.com"
              value={from}
              onChange={(e) => setFrom(e.target.value)}
              onBlur={() => setTouched(true)}
              error={fromError}
              helperText={fromError ? '이메일 형식을 확인해주세요.' : undefined}
            />
          </FormField>
          <FormField label="제목" htmlFor="support-subject" required>
            <TextField id="support-subject" fullWidth value={subject} onChange={(e) => setSubject(e.target.value)} />
          </FormField>
          <FormField label="내용" htmlFor="support-body" required>
            <TextField
              id="support-body"
              fullWidth
              multiline
              minRows={6}
              placeholder="소속 기업명과 문의 내용을 적어주세요."
              value={body}
              onChange={(e) => setBody(e.target.value)}
            />
          </FormField>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button variant="outlined" onClick={onClose} disabled={sending}>
          취소
        </Button>
        <Button variant="contained" onClick={handleSend} disabled={!canSend}>
          보내기
        </Button>
      </DialogActions>
    </Dialog>
  );
}
