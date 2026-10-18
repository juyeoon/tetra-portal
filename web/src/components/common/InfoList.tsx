import type { ReactNode } from 'react';
import { Box, Stack } from '@mui/material';
import { line, size, text, type } from '@/theme/theme';

export interface InfoItem {
  label: string;
  value: ReactNode;
  action?: ReactNode;
}

interface InfoListProps {
  items: InfoItem[];
  /** 라벨 폭 — 긴 라벨(예: 할당 S3 Bucket URL)은 wide */
  labelWidth?: 'default' | 'wide';
}

/** 라벨(본문 · tertiary) / 값(본문 · primary) 2열 목록 */
export default function InfoList({ items, labelWidth = 'default' }: InfoListProps) {
  const width = labelWidth === 'wide' ? size.infoLabelWide : size.infoLabel;
  return (
    <Box>
      {items.map((item) => (
        <Stack
          key={item.label}
          direction="row"
          alignItems="center"
          spacing={2}
          sx={{ minHeight: size.rowMin, py: 1, borderBottom: `1px solid ${line.base}`, '&:last-of-type': { borderBottom: 0 } }}
        >
          <Box sx={{ width: { xs: size.infoLabelMobile, sm: width }, flexShrink: 0, color: text.tertiary, ...type.body }}>{item.label}</Box>
          <Box sx={{ flex: 1, minWidth: 0, ...type.body, color: text.primary, wordBreak: 'keep-all', overflowWrap: 'anywhere' }}>{item.value}</Box>
          {item.action && <Box sx={{ flexShrink: 0 }}>{item.action}</Box>}
        </Stack>
      ))}
    </Box>
  );
}
