import { TableBody, TableCell, TableHead, TableRow } from '@mui/material';
import TableCard from '@/components/common/TableCard';
import { color, font } from '@/theme/theme';
import { formatNumber } from '@/utils/format';

/**
 * 발급 쿠폰 내역 — 쿠폰별 수량은 없고 재고는 이벤트 하나에 하나(일괄 발급, 계획서 5-5).
 * "총 발급 재고"는 결과 탭의 "총 재고"와 같은 값
 */
export default function CouponTable({ coupons, stockCount }: { coupons: { name: string; description: string }[]; stockCount: number }) {
  return (
    <TableCard minWidth={480}>
      <TableHead>
        <TableRow>
          <TableCell width={56}>순서</TableCell>
          <TableCell>쿠폰 이름</TableCell>
          <TableCell>Description</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {coupons.map((c, i) => (
          <TableRow key={`${i}-${c.name}`}>
            <TableCell>{i + 1}</TableCell>
            <TableCell>{c.name}</TableCell>
            <TableCell>{c.description}</TableCell>
          </TableRow>
        ))}
        <TableRow sx={{ '& > td': { bgcolor: color.card, fontWeight: font.weight.semibold } }}>
          <TableCell colSpan={2}>총 발급 재고 (쿠폰 {coupons.length}종 공통)</TableCell>
          <TableCell align="right">{formatNumber(stockCount)}매</TableCell>
        </TableRow>
      </TableBody>
    </TableCard>
  );
}
