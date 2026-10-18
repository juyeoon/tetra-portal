import { Box } from '@mui/material';
import { code, font, radius, type } from '@/theme/theme';
import CopyButton from './CopyButton';

interface CodeBlockProps {
  children: string;
  /** 복사 버튼 표시 */
  copyable?: boolean;
  maxHeight?: number;
}

/** 어두운 배경 코드 블록 — 사전 가이드 예제 코드 */
export default function CodeBlock({ children, copyable = true, maxHeight }: CodeBlockProps) {
  return (
    <Box sx={{ position: 'relative', borderRadius: radius.lg, bgcolor: code.bg, color: code.fg }}>
      {copyable && (
        <Box sx={{ position: 'absolute', top: 8, right: 8, '& button': { color: code.muted, '&:hover': { color: code.fg, bgcolor: 'rgb(255 255 255 / 8%)' } } }}>
          <CopyButton value={children} toast="코드가 복사되었습니다." />
        </Box>
      )}
      <Box
        component="pre"
        sx={{
          m: 0,
          p: 2.5,
          pr: copyable ? 6 : 2.5,
          overflow: 'auto',
          maxHeight,
          ...type.monoSm,
          lineHeight: 1.7,
          fontWeight: font.weight.regular,
          tabSize: 2,
        }}
      >
        <code>{children}</code>
      </Box>
    </Box>
  );
}
