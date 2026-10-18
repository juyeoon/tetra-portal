import { TableBody, TableCell, TableHead, TableRow } from '@mui/material';
import TableCard from '@/components/common/TableCard';
import { color, font } from '@/theme/theme';
import type { Coupon } from '@/types';
import { totalCouponStock } from '@/utils/event';
import { formatNumber } from '@/utils/format';

/** 발급 쿠폰 내역 — "총 발급 재고"는 매수 합계(결과 탭의 "총 재고"와 같은 값) */
export default function CouponTable({ coupons }: { coupons: Coupon[] }) {
  return (
    <TableCard minWidth={480}>
      <TableHead>
        <TableRow>
          <TableCell>쿠폰 이름</TableCell>
          <TableCell>Description</TableCell>
          <TableCell align="right">매수</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {coupons.map((c) => (
          <TableRow key={c.name}>
            <TableCell>{c.name}</TableCell>
            <TableCell>{c.description}</TableCell>
            <TableCell align="right">{formatNumber(c.quantity)}</TableCell>
          </TableRow>
        ))}
        <TableRow sx={{ '& > td': { bgcolor: color.card, fontWeight: font.weight.semibold } }}>
          <TableCell colSpan={2}>총 발급 재고</TableCell>
          <TableCell align="right">{formatNumber(totalCouponStock(coupons))}</TableCell>
        </TableRow>
      </TableBody>
    </TableCard>
  );
}
