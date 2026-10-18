import type { ReactNode } from 'react';
import { Table, TableContainer } from '@mui/material';

/** Snow 표 — 카드 없이 페이지 위에 바로 놓고 선으로만 구분 */
export default function TableCard({ children, minWidth = 880 }: { children: ReactNode; minWidth?: number }) {
  return (
    <TableContainer>
      <Table sx={{ minWidth }}>{children}</Table>
    </TableContainer>
  );
}
