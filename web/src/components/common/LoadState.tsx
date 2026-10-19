import { Box, Button, CircularProgress } from '@mui/material';
import ErrorOutlineRoundedIcon from '@mui/icons-material/ErrorOutlineRounded';
import StateMessage from './StateMessage';

/** 화면 데이터를 불러오는 중 */
export function LoadingView({ label = '불러오는 중' }: { label?: string }) {
  return (
    <Box sx={{ display: 'grid', placeItems: 'center', py: { xs: 8, md: 12 } }}>
      <CircularProgress aria-label={label} />
    </Box>
  );
}

/** 불러오지 못했을 때 — 서버 문구 + 다시 시도 */
export function ErrorView({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <StateMessage icon={<ErrorOutlineRoundedIcon />} tone="red" title="불러오지 못했습니다" description={message}>
      <Button variant="outlined" onClick={onRetry} sx={{ mt: 2 }}>
        다시 시도
      </Button>
    </StateMessage>
  );
}
