import { useRef, useState, type ChangeEvent, type ReactNode } from 'react';
import {
  Box,
  Button,
  ButtonBase,
  Card,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  Link,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import ArrowBackRoundedIcon from '@mui/icons-material/ArrowBackRounded';
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded';
import CloseRoundedIcon from '@mui/icons-material/CloseRounded';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import SectionTitle from '@/components/common/SectionTitle';
import StatusChip from '@/components/common/StatusChip';
import { color, font, line, radius, size, text, tones, type } from '@/theme/theme';
import type { Plan } from '@/types';
import { formatCapacity } from '@/utils/event';
import { formatNumber } from '@/utils/format';
import { eventTypeLabel } from '@/utils/status';
import { isHttpUrl } from '@/utils/validation';
import {
  APPROVE_MIN_LEAD,
  BANNER_TYPES,
  HOSTING_LEAD_BEFORE_START,
  MAX_BANNER_BYTES,
  MAX_COUPONS,
  MAX_STOCK,
  SCHEDULE_GAP,
  emptyCoupon,
  infoErrors,
  type ApplyDraft,
} from './draft';

interface Props {
  draft: ApplyDraft;
  /** GET /plans — 받기 전 undefined */
  plans?: Plan[];
  plansError?: string;
  onRetryPlans: () => void;
  onChange: (patch: Partial<ApplyDraft>) => void;
  onPrev: () => void;
  onNext: () => void;
}

function Label({ children, required, htmlFor, extra }: { children: ReactNode; required?: boolean; htmlFor?: string; extra?: ReactNode }) {
  return (
    <Stack direction="row" alignItems="center" spacing={0.5} sx={{ mb: 1 }}>
      <Typography component="label" htmlFor={htmlFor} variant="body1" sx={{ fontWeight: font.weight.medium }}>
        {children}
        {required && (
          <Box component="span" sx={{ color: tones.red.dot, ml: 0.5 }}>
            *
          </Box>
        )}
      </Typography>
      {extra}
    </Stack>
  );
}

function Note({ children }: { children: ReactNode }) {
  return (
    <Typography variant="body2" sx={{ mt: 0.75, color: text.tertiary }}>
      ※ {children}
    </Typography>
  );
}

function ErrorText({ show, children }: { show: boolean; children?: string }) {
  if (!show || !children) return null;
  return (
    <Typography variant="body2" sx={{ mt: 0.75, color: tones.red.fg }}>
      {children}
    </Typography>
  );
}

function PeriodFields({ id, start, end, onStart, onEnd, error }: { id: string; start: string; end: string; onStart: (v: string) => void; onEnd: (v: string) => void; error: boolean }) {
  return (
    <Box sx={{ display: 'grid', gap: 1.5, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
      <TextField id={`${id}-start`} type="datetime-local" fullWidth value={start} onChange={(e) => onStart(e.target.value)} error={error} helperText="시작 날짜·시각" />
      <TextField id={`${id}-end`} type="datetime-local" fullWidth value={end} onChange={(e) => onEnd(e.target.value)} error={error} helperText="종료 날짜·시각" />
    </Box>
  );
}

/** 이미지 크기 읽기 */
function readImageSize(file: File) {
  return new Promise<{ width: number; height: number }>((resolve) => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      resolve({ width: img.naturalWidth, height: img.naturalHeight });
      URL.revokeObjectURL(url);
    };
    img.onerror = () => {
      resolve({ width: 0, height: 0 });
      URL.revokeObjectURL(url);
    };
    img.src = url;
  });
}

/** UI-TP-08 이벤트 신청 - Tab2 (이벤트 정보) */
export default function InfoStep({ draft, plans, plansError, onRetryPlans, onChange, onPrev, onNext }: Props) {
  const fileRef = useRef<HTMLInputElement>(null);
  const [limitOpen, setLimitOpen] = useState(false);
  const [planInfoOpen, setPlanInfoOpen] = useState(false);
  const [urlChecked, setUrlChecked] = useState(false);
  /** 이름 칸을 건드렸으면(입력 · 포커스 이탈) 오류를 바로 보여 준다 — 처음 열 때부터 빨간 글씨가 뜨지 않게 */
  const [nameTouched, setNameTouched] = useState(draft.name !== '');
  /** 고른 파일이 형식 · 크기 조건에 맞지 않을 때 */
  const [bannerError, setBannerError] = useState<string | null>(null);

  const errors = infoErrors(draft);
  const canNext = Object.keys(errors).length === 0;
  const isCoupon = draft.type === 'COUPON';
  const urlValid = isHttpUrl(draft.endpointUrl);
  const eventPeriodFilled = !!(draft.eventStart && draft.eventEnd);
  const hostingPeriodFilled = !!(draft.hostingStart && draft.hostingEnd);
  const urlVerified = draft.verifiedUrl !== '' && draft.verifiedUrl === draft.endpointUrl;

  const setCoupon = (i: number, patch: Partial<ApplyDraft['coupons'][number]>) => onChange({ coupons: draft.coupons.map((c, j) => (j === i ? { ...c, ...patch } : c)) });

  const addCoupon = () => {
    if (draft.coupons.length >= MAX_COUPONS) setLimitOpen(true);
    else onChange({ coupons: [...draft.coupons, emptyCoupon()] });
  };

  const onFile = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    // 파일은 신청할 때 함께 보낸다(서버가 형식 · 크기를 다시 검사). 여기서는 먼저 걸러 준다
    if (!BANNER_TYPES.includes(file.type)) {
      setBannerError('PNG 또는 JPG 파일만 올릴 수 있습니다.');
      return;
    }
    if (file.size > MAX_BANNER_BYTES) {
      setBannerError('2MB 이하 파일만 올릴 수 있습니다.');
      return;
    }
    setBannerError(null);
    const { width, height } = await readImageSize(file);
    onChange({ banner: file, bannerSize: width ? { width, height } : null });
  };

  // 총 발급 재고 — 이벤트 하나에 하나 (쿠폰별 수량 없음, 계획서 5-5)
  const stockField = (
    <Box>
      <Label required htmlFor="stock-count">
        총 발급 재고
      </Label>
      <TextField
        id="stock-count"
        placeholder="0"
        value={draft.stock ? formatNumber(Number(draft.stock)) : ''}
        onChange={(e) => onChange({ stock: e.target.value.replace(/[^\d]/g, '').slice(0, 9) })}
        error={draft.stock !== '' && !!errors.stock}
        slotProps={{ htmlInput: { inputMode: 'numeric', style: { textAlign: 'right' } }, input: { endAdornment: <Typography variant="body2" sx={{ color: text.tertiary, ml: 0.5 }}>매</Typography> } }}
        sx={{ width: { xs: '100%', sm: 240 } }}
      />
      <Note>
        {isCoupon ? '등록한 쿠폰 전체에 공통으로 적용되는 재고입니다 (쿠폰별 수량 없음)' : '선착순으로 발급할 전체 수량입니다'} · 최대 {formatNumber(MAX_STOCK)}매
      </Note>
      <ErrorText show={draft.stock !== ''}>{errors.stock}</ErrorText>
    </Box>
  );

  // URL 형식만 검증 — 실제 접속 여부는 확인하지 않는다
  const checkUrl = () => {
    setUrlChecked(true);
    if (urlValid) onChange({ verifiedUrl: draft.endpointUrl });
  };

  return (
    <>
      <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 3 }}>
        <Typography variant="body1" sx={{ color: text.secondary }}>
          선택된 이벤트 타입
        </Typography>
        <Box component="span" sx={{ px: 1.25, py: 0.25, borderRadius: radius.sm, bgcolor: color.pointTint, color: color.pointDeep, ...type.caption, fontWeight: font.weight.semibold }}>
          {draft.type ? eventTypeLabel[draft.type] : '-'}
        </Box>
        <Link component="button" type="button" onClick={onPrev} sx={{ ...type.caption }}>
          타입 변경
        </Link>
      </Stack>

      <Stack spacing={3}>
        {/* ① 기본 정보 · ② 배너 */}
        <Card sx={{ p: 3 }}>
          <SectionTitle>이벤트 기본 정보</SectionTitle>
          <Stack spacing={3}>
            <Box>
              <Label required htmlFor="event-name">
                이벤트 이름
              </Label>
              <TextField
                id="event-name"
                fullWidth
                placeholder="예: 9월 얼리버드 타임세일"
                value={draft.name}
                onChange={(e) => {
                  onChange({ name: e.target.value });
                  setNameTouched(true);
                }}
                onBlur={() => setNameTouched(true)}
                error={nameTouched && !!errors.name}
              />
              <ErrorText show={nameTouched}>{errors.name}</ErrorText>
            </Box>
            <Box>
              <Label required htmlFor="event-period-start">
                이벤트 기간
              </Label>
              <PeriodFields
                id="event-period"
                start={draft.eventStart}
                end={draft.eventEnd}
                onStart={(v) => onChange({ eventStart: v })}
                onEnd={(v) => onChange({ eventEnd: v })}
                error={eventPeriodFilled && !!errors.eventPeriod}
              />
              <ErrorText show={eventPeriodFilled}>{errors.eventPeriod}</ErrorText>
            </Box>
            <Box>
              <Label required htmlFor="hosting-period-start">
                도메인 호스팅 기간
              </Label>
              <PeriodFields
                id="hosting-period"
                start={draft.hostingStart}
                end={draft.hostingEnd}
                onStart={(v) => onChange({ hostingStart: v })}
                onEnd={(v) => onChange({ hostingEnd: v })}
                error={hostingPeriodFilled && !!errors.hostingPeriod}
              />
              <Note>
                호스팅 시작은 지금부터 {APPROVE_MIN_LEAD}분 이후, 이벤트 시작 {HOSTING_LEAD_BEFORE_START + SCHEDULE_GAP}분 전까지 · 도메인 호스팅 시간(시간 단위)만큼 이벤트 비용에 포함됩니다 · 한국 시각 기준
              </Note>
              <ErrorText show={hostingPeriodFilled}>{errors.hostingPeriod}</ErrorText>
            </Box>
            <Box>
              <Label required>이벤트 대표 배너 이미지</Label>
              <Stack direction="row" spacing={1}>
                <Button variant="outlined" onClick={() => fileRef.current?.click()} sx={{ flexShrink: 0 }}>
                  파일 선택
                </Button>
                <TextField
                  fullWidth
                  value={draft.banner ? `${draft.banner.name}${draft.bannerSize ? ` (${draft.bannerSize.width}×${draft.bannerSize.height})` : ''}` : ''}
                  error={!!bannerError}
                  placeholder="선택된 파일 없음"
                  slotProps={{ htmlInput: { readOnly: true, 'aria-label': '선택된 배너 파일' } }}
                />
                <input ref={fileRef} type="file" accept="image/png,image/jpeg" hidden onChange={onFile} />
              </Stack>
              <Note>권장 크기 1200×420 · PNG, JPG · 2MB 이하 · 대기열·이벤트 화면 상단에 노출됩니다</Note>
              <ErrorText show={!!bannerError}>{bannerError ?? undefined}</ErrorText>
            </Box>
            {!isCoupon && stockField}
          </Stack>
        </Card>

        {/* ③④⑤ 발급할 쿠폰 정보 — 쿠폰 타입일 때만 */}
        {isCoupon && (
          <Card sx={{ p: 3 }}>
            <SectionTitle
              action={
                <Button size="small" variant="outlined" startIcon={<AddRoundedIcon />} onClick={addCoupon}>
                  쿠폰 행 추가
                </Button>
              }
            >
              발급할 쿠폰 정보
            </SectionTitle>

            <Box sx={{ display: { xs: 'none', sm: 'grid' }, gridTemplateColumns: '1fr 2fr 36px', gap: 1, mb: 1, px: 0.5 }}>
              {['쿠폰 이름', 'Description (화면 노출 문구)', ''].map((h) => (
                <Typography key={h} variant="body2" sx={{ color: text.tertiary }}>
                  {h}
                </Typography>
              ))}
            </Box>
            <Stack spacing={1}>
              {draft.coupons.map((c, i) => (
                <Box key={i} sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr 36px', sm: '1fr 2fr 36px' }, gap: 1, alignItems: 'center' }}>
                  <TextField
                    size="small"
                    placeholder="예: 10% 할인 쿠폰"
                    value={c.name}
                    onChange={(e) => setCoupon(i, { name: e.target.value })}
                    slotProps={{ htmlInput: { 'aria-label': `쿠폰 ${i + 1} 이름` } }}
                    sx={{ gridColumn: { xs: '1 / 2', sm: 'auto' } }}
                  />
                  <TextField
                    size="small"
                    placeholder="예: 전 상품 적용"
                    value={c.description}
                    onChange={(e) => setCoupon(i, { description: e.target.value })}
                    slotProps={{ htmlInput: { 'aria-label': `쿠폰 ${i + 1} Description` } }}
                    sx={{ gridColumn: { xs: '1 / 2', sm: 'auto' }, gridRow: { xs: 2, sm: 'auto' } }}
                  />
                  <IconButton
                    aria-label={`쿠폰 ${i + 1} 삭제`}
                    onClick={() => onChange({ coupons: draft.coupons.filter((_, j) => j !== i) })}
                    disabled={draft.coupons.length === 1}
                    sx={{ gridColumn: { xs: 2, sm: 'auto' }, gridRow: { xs: '1 / 3', sm: 'auto' }, '&:hover': { color: tones.red.fg, bgcolor: tones.red.bg } }}
                  >
                    <CloseRoundedIcon sx={{ fontSize: size.iconMd }} />
                  </IconButton>
                </Box>
              ))}
            </Stack>

            <Typography variant="body2" sx={{ mt: 1, textAlign: 'right', color: text.secondary }}>
              쿠폰 {draft.coupons.length}종 / 최대 {MAX_COUPONS}종 · 위에서부터 이벤트 화면에 노출
            </Typography>
            <ErrorText show={draft.coupons.some((c) => c.name || c.description)}>{errors.coupons}</ErrorText>
            <Box sx={{ mt: 3 }}>{stockField}</Box>
          </Card>
        )}

        {/* ⑥ 트래픽 플랜 · ⑦⑧ 엔드포인트 URL */}
        <Card sx={{ p: 3 }}>
          <SectionTitle>트래픽 및 연동 정보</SectionTitle>
          <Stack spacing={3}>
            <Box>
              <Label required>트래픽 수용 인원</Label>
              {plansError ? (
                <Stack direction="row" spacing={1} alignItems="center">
                  <Typography variant="body2" sx={{ color: tones.red.fg }}>
                    요금제를 불러오지 못했습니다. {plansError}
                  </Typography>
                  <Button size="small" variant="outlined" onClick={onRetryPlans}>
                    다시 시도
                  </Button>
                </Stack>
              ) : !plans ? (
                <CircularProgress size={24} aria-label="요금제 불러오는 중" />
              ) : (
              <Box role="radiogroup" aria-label="트래픽 수용 인원 플랜" sx={{ display: 'grid', gap: 1.5, gridTemplateColumns: { xs: 'repeat(2, 1fr)', md: `repeat(${Math.min(plans.length, 4)}, 1fr)` } }}>
                {plans.map(({ planCode: p, maxCapacity }) => {
                  const selected = draft.plan === p;
                  return (
                    <ButtonBase
                      key={p}
                      role="radio"
                      aria-checked={selected}
                      onClick={() => onChange({ plan: p })}
                      sx={{
                        py: 1.5,
                        flexDirection: 'column',
                        borderRadius: radius.lg,
                        border: `1px solid ${selected ? color.point : line.strong}`,
                        bgcolor: selected ? color.pointTint : color.surface,
                        boxShadow: selected ? `inset 0 0 0 1px ${color.point}` : 'none',
                        '&:hover': { borderColor: selected ? color.point : color.navy },
                      }}
                    >
                      <Typography variant="subtitle1" sx={{ color: selected ? color.pointDeep : text.primary }}>
                        {p} · {formatCapacity(maxCapacity)}
                      </Typography>
                      <Typography variant="body2" sx={{ color: text.tertiary }}>
                        동시 접속 {formatNumber(maxCapacity)}명
                      </Typography>
                    </ButtonBase>
                  );
                })}
              </Box>
              )}
              <Note>
                선택한 규모를 초과하면 추가요금(extra charge)이 발생할 수 있습니다.{' '}
                <Link component="button" type="button" onClick={() => setPlanInfoOpen(true)} sx={{ ...type.caption }}>
                  자세히보기
                </Link>
              </Note>
            </Box>

            <Box>
              <Label
                required
                htmlFor="endpoint-url"
                extra={
                  <Tooltip title="대기열을 통과한 고객이 이동하는 고객사 페이지(이벤트 진입점) 주소입니다." arrow>
                    <Box component="span" tabIndex={0} aria-label="이벤트 엔드포인트 URL 안내" sx={{ display: 'inline-flex', color: text.tertiary, cursor: 'help' }}>
                      <InfoOutlinedIcon sx={{ fontSize: size.iconSm }} />
                    </Box>
                  </Tooltip>
                }
              >
                이벤트 엔드포인트 URL
              </Label>
              <Stack direction="row" spacing={1}>
                <TextField
                  id="endpoint-url"
                  fullWidth
                  placeholder="https://shop.example.com/tetra-entry"
                  value={draft.endpointUrl}
                  onChange={(e) => {
                    onChange({ endpointUrl: e.target.value });
                    setUrlChecked(false);
                  }}
                  error={urlChecked && !urlValid}
                />
                <Button variant="outlined" onClick={checkUrl} disabled={!draft.endpointUrl.trim()} sx={{ flexShrink: 0, minWidth: 80 }}>
                  확인
                </Button>
              </Stack>
              {urlVerified ? (
                <Stack direction="row" spacing={0.5} alignItems="center" sx={{ mt: 0.75, color: tones.green.fg }}>
                  <CheckCircleRoundedIcon sx={{ fontSize: size.iconSm }} />
                  <Typography variant="body2" sx={{ color: 'inherit' }}>
                    올바른 URL 형식입니다. (형식만 검증 · 실제 연결 여부는 확인하지 않음)
                  </Typography>
                </Stack>
              ) : (
                <ErrorText show={urlChecked && !urlValid}>{errors.endpointUrl}</ErrorText>
              )}
            </Box>
          </Stack>
        </Card>
      </Stack>

      {/* ⑨ 이전 단계로 · ⑩ 다음 단계 */}
      <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={2} sx={{ mt: 4 }}>
        <Button variant="outlined" size="large" startIcon={<ArrowBackRoundedIcon />} onClick={onPrev}>
          이전 단계로
        </Button>
        <Stack direction="row" alignItems="center" spacing={2}>
          {!canNext && (
            <Typography variant="body2" sx={{ display: { xs: 'none', md: 'block' }, color: text.tertiary }}>
              모든 필수 항목을 입력하고 URL 확인을 마치면 다음 단계로 진행할 수 있습니다.
            </Typography>
          )}
          <Button variant="contained" size="large" onClick={onNext} disabled={!canNext} sx={{ minWidth: 140 }}>
            다음 단계
          </Button>
        </Stack>
      </Stack>

      {/* ④ 쿠폰 10종 초과 경고 */}
      <Dialog open={limitOpen} onClose={() => setLimitOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>쿠폰을 더 추가할 수 없습니다</DialogTitle>
        <DialogContent>
          <Typography variant="body1" sx={{ color: text.secondary }}>
            쿠폰은 최대 {MAX_COUPONS}종까지 등록할 수 있습니다.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button variant="contained" onClick={() => setLimitOpen(false)}>
            확인
          </Button>
        </DialogActions>
      </Dialog>

      {/* ⑥ 플랜·추가요금 자세히보기 */}
      <Dialog open={planInfoOpen} onClose={() => setPlanInfoOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>트래픽 수용 인원 플랜</DialogTitle>
        <DialogContent>
          <Stack spacing={1}>
            {(plans ?? []).map((p) => (
              <Stack key={p.planCode} direction="row" spacing={1} alignItems="center" sx={{ py: 1, borderBottom: `1px solid ${line.base}` }}>
                <StatusChip label={p.planCode} tone="grey" />
                <Typography variant="body1">동시 접속 {formatNumber(p.maxCapacity)}명</Typography>
              </Stack>
            ))}
          </Stack>
          <Typography variant="body2" sx={{ mt: 2, color: text.secondary }}>
            선택한 규모를 넘는 접속이 발생하면 자동으로 대기열이 적용되며, 초과 사용분에 추가요금(extra charge)이 발생할 수 있습니다.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button variant="contained" onClick={() => setPlanInfoOpen(false)}>
            확인
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
