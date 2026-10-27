import { Box, Card, Stack, Typography } from '@mui/material';
import SectionTitle from '@/components/common/SectionTitle';
import StatusChip from '@/components/common/StatusChip';
import { color, font, radius, text } from '@/theme/theme';
import { TEXT, countText, rejectChip } from '../monitoringView';

/** 연동 확인 — 입장 실패(JWT 검증 실패 · 재사용) 최근 5분. 고객사 쪽 설정 문제라 표시한다 */
export default function IntegrationCheck({ rejects5m }: { rejects5m: number | null }) {
  const chip = rejectChip(rejects5m);
  return (
    <Card sx={{ p: 3, bgcolor: color.card }}>
      <SectionTitle mb={0.5}>연동 확인</SectionTitle>
      <Typography variant="body2" sx={{ mb: 2, color: text.tertiary }}>
        고객사 쪽 설정 문제로 생기는 실패예요 · 최근 5분
      </Typography>
      <Stack direction="row" spacing={2.5} alignItems="center" sx={{ p: 2.5, bgcolor: color.white, borderRadius: radius.md }}>
        {chip && <StatusChip label={chip.label} tone={chip.tone} />}
        <Box>
          <Typography variant="body1" sx={{ fontWeight: font.weight.semibold }}>
            입장 실패 {countText(rejects5m, '건')}
          </Typography>
          <Typography variant="body2" sx={{ mt: 0.25, color: text.tertiary }}>
            {TEXT.rejectsDescription}
          </Typography>
        </Box>
      </Stack>
    </Card>
  );
}
