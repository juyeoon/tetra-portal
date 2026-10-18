import { Button, IconButton, Tooltip } from '@mui/material';
import ContentCopyRoundedIcon from '@mui/icons-material/ContentCopyRounded';
import { useToast } from '@/store/ToastContext';
import { size } from '@/theme/theme';
import { copyText } from '@/utils/format';

interface CopyButtonProps {
  value: string;
  /** 지정하면 텍스트 버튼, 없으면 아이콘 버튼 */
  label?: string;
  toast?: string;
}

export default function CopyButton({ value, label, toast = '클립보드에 복사되었습니다.' }: CopyButtonProps) {
  const show = useToast();
  const onCopy = async () => {
    const ok = await copyText(value);
    show(ok ? toast : '복사에 실패했습니다.', ok ? 'success' : 'error');
  };

  if (label) {
    return (
      <Button size="small" variant="outlined" onClick={onCopy}>
        {label}
      </Button>
    );
  }
  return (
    <Tooltip title="복사">
      <IconButton size="small" onClick={onCopy} aria-label="복사" sx={{ p: 0.5 }}>
        <ContentCopyRoundedIcon sx={{ fontSize: size.iconSm }} />
      </IconButton>
    </Tooltip>
  );
}
