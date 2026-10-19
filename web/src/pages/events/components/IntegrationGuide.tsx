import { useState, type ReactNode } from 'react';
import { Box, Button, ButtonBase, Card, Stack, Typography } from '@mui/material';
import OpenInNewRoundedIcon from '@mui/icons-material/OpenInNewRounded';
import CodeBlock from '@/components/common/CodeBlock';
import SectionTitle from '@/components/common/SectionTitle';
import { useToast } from '@/store/ToastContext';
import { color, font, line, radius, size, text, type } from '@/theme/theme';
import type { EventDetail } from '@/types';
import { GUIDE_LANGUAGES, jwtExample, jwtStructure, sessionForm, type GuideLanguage } from './guideSnippets';

/** TODO: 연동 가이드 전체 문서 주소가 정해지면 넣는다 */
const GUIDE_DOC_URL = '';

function Notes({ items }: { items: ReactNode[] }) {
  return (
    <Stack spacing={0.25} sx={{ mt: 1.5 }}>
      {items.map((item, i) => (
        <Typography key={i} variant="body2" sx={{ color: text.tertiary }}>
          ※ {item}
        </Typography>
      ))}
    </Stack>
  );
}

function Lead({ children }: { children: ReactNode }) {
  return (
    <Typography variant="body1" sx={{ mt: -1, mb: 2, color: text.secondary }}>
      {children}
    </Typography>
  );
}

/** UI-TP-6-1 이벤트 상세 - 사전 가이드 */
export default function IntegrationGuide({ event }: { event: EventDetail }) {
  const toast = useToast();
  const [lang, setLang] = useState<GuideLanguage>('Java');
  const domain = event.domain ?? '{승인 후 발급되는 도메인}';

  const openGuideDoc = () => {
    if (GUIDE_DOC_URL) window.open(GUIDE_DOC_URL, '_blank', 'noopener');
    else toast('전체 가이드 문서는 준비 중입니다.', 'info');
  };

  return (
    <Stack spacing={3}>
      <Card sx={{ p: 3 }}>
        <SectionTitle>JWT 토큰 구성</SectionTitle>
        <Lead>고객사 서버가 이벤트 입장 시 아래 구성으로 JWT를 만들어 세션 발급 URL로 전달합니다.</Lead>
        <CodeBlock>{jwtStructure(String(event.eventId))}</CodeBlock>
        <Notes
          items={[
            '서명(HS256)에는 운영자가 전달한 연동키(Secret Key)를 사용합니다. 연동키는 설정·연동 관리자에게 별도로 발급받습니다.',
            '토큰 유효 시간은 1~2분이며, 한 번 사용한 토큰(jti)은 다시 사용할 수 없습니다.',
            '대기 시간 동안 유지되는 토큰이 아니므로 입장 직전에 발급하세요.',
          ]}
        />
      </Card>

      <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', lg: 'minmax(0, 1.2fr) minmax(0, 1fr)' }, alignItems: 'start' }}>
        <Card sx={{ p: 3, minWidth: 0 }}>
          <SectionTitle
            action={
              <Button size="small" variant="outlined" endIcon={<OpenInNewRoundedIcon sx={{ fontSize: `${size.iconSm} !important` }} />} onClick={openGuideDoc}>
                전체 가이드 문서 보기
              </Button>
            }
          >
            API 연동 가이드 — JWT 발급 예제
          </SectionTitle>

          {/* 언어 선택 탭 — 선택한 탭은 포인트 색 */}
          <Stack role="tablist" aria-label="예제 언어" direction="row" flexWrap="wrap" useFlexGap spacing={0.75} sx={{ mb: 1.5 }}>
            {GUIDE_LANGUAGES.map((l) => {
              const selected = l === lang;
              return (
                <ButtonBase
                  key={l}
                  role="tab"
                  aria-selected={selected}
                  onClick={() => setLang(l)}
                  sx={{
                    height: size.controlSm,
                    px: 1.5,
                    borderRadius: radius.md,
                    border: `1px solid ${selected ? color.point : line.base}`,
                    bgcolor: selected ? color.point : 'transparent',
                    color: selected ? color.onPoint : text.secondary,
                    ...type.caption,
                    fontWeight: selected ? font.weight.semibold : font.weight.medium,
                    '&:hover': { borderColor: selected ? color.point : line.strong },
                  }}
                >
                  {l}
                </ButtonBase>
              );
            })}
          </Stack>
          <CodeBlock maxHeight={420}>{jwtExample(lang, String(event.eventId))}</CodeBlock>
        </Card>

        <Card sx={{ p: 3, minWidth: 0 }}>
          <SectionTitle>세션 발급 요청 (히든폼)</SectionTitle>
          <Lead>발급한 JWT를 숨은 입력값(token)에 담아 이벤트 도메인의 세션 발급 URL로 GET 요청합니다.</Lead>
          <CodeBlock>{sessionForm(domain)}</CodeBlock>
          <Notes items={['서명 만료·jti 검증에 실패하면 세션이 발급되지 않습니다.', 'JWT를 URL로 전달하므로 만료 시간을 짧게 유지하세요.']} />
        </Card>
      </Box>
    </Stack>
  );
}
