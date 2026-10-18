# Tetra Portal — 화면 (web)

React 19 + TypeScript + Vite 7 + MUI 7 (+ MUI X Charts) · React Router 7

운영 Console(`tetra/app/console`의 `web/`)의 구조와 디자인 토큰을 복사해서 시작했습니다. 공통 라이브러리로 묶지 않았으므로 두 저장소는 각각 수정합니다. 저장소 전체 안내(브랜치·커밋·MR 규칙, GitLab 접속)는 [루트 README](../README.md)를 보세요.

> **현재 API 미연동 상태입니다.** 모든 데이터는 `src/mocks/*`에 있습니다. API를 붙일 위치는 `TODO:` 주석으로 표시해 두었습니다. 새로고침하면 데이터가 처음 상태로 돌아갑니다.

---

## 실행

```bash
npm install
npm run dev          # http://localhost:5174
```

| 명령 | 내용 |
| --- | --- |
| `npm run dev` | 개발 서버 (포트 5174 — Console 5173과 동시 실행 가능) |
| `npm run typecheck` | 타입 검사만 |
| `npm run build` | 타입 검사 + 프로덕션 빌드 (`dist/`) — **MR 전에 통과 확인** |
| `npm run preview` | 빌드 결과 미리보기 |

- Node.js **20.19 이상 또는 22.12 이상** (개발 확인 버전 24.19)
- 개발 서버를 띄운 직후에는 화면을 처음 열 때 컴파일 때문에 1~2초 빈 화면이 보일 수 있습니다.
- `npm install` 중 `allow-scripts … esbuild (postinstall)` 경고가 나와도 빌드에는 문제가 없습니다.

---

## 테스트 계정

| 구분 | 이메일 | 비밀번호 | 결과 |
| --- | --- | --- | --- |
| 테넌트 관리자 | `admin@tenant-company.com` | 아무 값 (1자 이상) | 로그인 성공 → `/events` |
| 로그인 실패 확인용 | 그 외 아무 이메일 (예: `tenant01@gmail.com`) | 아무 값 | 하단 "로그인 실패" 배너 (UI-TP-02) |

로그인한 계정 정보(mock, `src/mocks/account.ts`)는 아래와 같습니다.

| 항목 | 값 |
| --- | --- |
| 소속 기업 | 테넌트컴퍼니 |
| 테넌트 ID | `tnt-3f8a21` |
| 담당자(신청자 표시) | 다란 (테넌트 관리자) |
| 이메일 (아이디) | `admin@tenant-company.com` |
| 고객센터 수신 주소 | `tetraops@gmail.com` |

> mock에는 로그인 세션이 없어서, 로그인하지 않아도 `/events` 등 모든 주소에 바로 들어갈 수 있습니다. 라우트 보호는 로그인 API를 연동할 때 추가합니다.

---

## 테스트 시나리오

### 이벤트 (`/events/:eventId`)

이벤트 ID 형식은 `{테넌트ID}-evt-{번호}-01`입니다.

| 이벤트 ID | 이벤트명 | 신청상태 | 호스팅 / 이벤트상태 | 확인할 것 |
| --- | --- | --- | --- | --- |
| `tnt-3f8a21-evt-1041-01` | 추석맞이 쿠폰 발급 | 승인됨 | 호스팅 종료 / 만료 | **결과 탭** 지표·차트·CSV 다운로드, 도메인 복사 |
| `tnt-3f8a21-evt-1042-01` | 9월 얼리버드 타임세일 | 승인됨 | 호스팅 중 / 진행 중 | 결과 탭 "집계 전" 안내, 사전 가이드에 실제 도메인 |
| `tnt-3f8a21-evt-1046-01` | 가을 신상품 할인 쿠폰 | 승인 대기 중 | - / - | 도메인 "승인 후 발급" |
| `tnt-3f8a21-evt-1047-01` | 블랙프라이데이 프리뷰 | 신청됨(처리 중) | - / - | XL 플랜, 추가 트래픽 산정 포함 |
| `tnt-3f8a21-evt-1039-01` | 여름 시즌오프 세일 | 신청됨(처리 중) | - / - | |
| `tnt-3f8a21-evt-1044-01` | 할로윈 한정 쿠폰 | 신청 실패 | - / - | 상태 처리 기록의 실패 사유 |
| `tnt-3f8a21-evt-1035-01` | 신규 회원 웰컴 쿠폰 | 반려됨 | - / - | 상태 처리 기록의 **반려 사유** |

- 상세 탭은 주소로 바로 열 수 있습니다: `?tab=application` (신청 내용) · `?tab=guide` (사전 가이드) · `?tab=result` (결과)
- 없는 ID로 들어가면 "이벤트를 찾을 수 없습니다" 화면이 나옵니다.

### 이벤트 신청 (`/events/new`)

아래 값으로 4단계를 끝까지 진행하면 신청됨(처리 중) 상태의 이벤트가 목록 맨 앞에 추가됩니다.

| 단계 | 입력 예시 | 확인할 것 |
| --- | --- | --- |
| 1. 이벤트 타입 | 쿠폰 [선택] | 타임세일·라이브커머스는 쿠폰 입력 영역이 없음 |
| 2. 이벤트 정보 | 이름 `10월 회원 감사 쿠폰` · 이벤트 `2026-10-20 00:00 ~ 10-26 23:59` · 호스팅 `2026-10-19 00:00 ~ 10-27 23:59` · 배너 아무 PNG/JPG · 쿠폰 2행(10,000 / 5,000매) · 플랜 M · URL `https://shop.example.com/tetra-entry` → [확인] | 모든 필수 항목 + URL 확인 전에는 [다음 단계] 비활성. 쿠폰 행 11번째 추가 시 경고 모달. 호스팅 기간이 이벤트 기간을 벗어나면 오류. URL에 `abc` 입력 후 [확인] → 형식 오류 |
| 3. 화면 설정 | 위젯 위치·배경 테마 변경 | 오른쪽 미리보기에 바로 반영 |
| 4. 신청서 제출 | [이벤트 미리보기] → [신청하기] | 예상 비용 = 호스팅 시간 × (1,200원 + 플랜 시간당 단가) |

### 그 밖의 화면

| 화면 | 확인할 것 |
| --- | --- |
| 로그인 → "고객센터" | 문의 메일 작성 모달 (회신 이메일은 로그인 폼 입력값으로 채워짐), [보내기] → 토스트 |
| 비밀번호 설정 | `/password/setup?token=test&email=admin@tenant-company.com` → 8자 이상 + 일치 시 [저장] 활성 → 완료 모달 → 로그인 / `?token=expired` → 만료 화면 |
| 리포트 `/reports` | [다운로드] → CSV 파일, [이메일 전송] → 로그인 계정 주소로 전송 토스트 |
| 알림 센터 `/notifications` | 안읽음 2건(파란 점) — 항목 클릭 시 읽음 처리, 필터 5종 건수, 검색 즉시 반영 |
| 설정 `/settings` | 이메일 수신 토글, [연동 해제] → 확인 모달 → 미연동 상태([Slack 연동하기]) — 알림 센터 채널 카드에도 반영, 채널명 `abc` 입력 → 형식 오류(`#`으로 시작해야 함) |
| 사용자 메뉴 (우측 상단) | 설정 이동, 로그아웃 → `/login` |

---

## 화면 / 라우트

| 화면 | Screen ID | 경로 | 파일 |
| --- | --- | --- | --- |
| 로그인 · 로그인 실패 | UI-TP-01 · 02 | `/login` | `pages/auth/LoginPage.tsx` |
| 고객센터 (모달) | UI-TP-03 | 로그인 화면 링크 | `pages/auth/components/SupportMailDialog.tsx` |
| 비밀번호 설정 | UI-TP-04 | `/password/setup?token=&email=` | `pages/auth/PasswordSetupPage.tsx` |
| 이벤트 내역 - 목록 (홈) | UI-TP-05 | `/events` (`/` → 이동) | `pages/events/EventListPage.tsx` |
| 이벤트 내역 - 상세 | UI-TP-06 · 6-1 · 6-2 | `/events/:eventId?tab=` | `pages/events/EventDetailPage.tsx` |
| 이벤트 신청 4단계 | UI-TP-07 ~ 10 | `/events/new` | `pages/events/apply/*` |
| 모니터링 | UI-TP-11 | 사이드바 비활성(준비중) — 라우트 없음 | `components/layout/Sidebar.tsx` |
| 리포트 | UI-TP-12 | `/reports` | `pages/reports/ReportPage.tsx` |
| 알림 센터 | UI-TP-13 | `/notifications` | `pages/notifications/NotificationCenterPage.tsx` |
| 설정 | UI-TP-14 | `/settings` | `pages/settings/SettingsPage.tsx` |

라우트 정의는 `src/App.tsx` 한 곳에 있습니다.

---

## 구조

```
src/
  assets/         # 로고(남색·흰색), 로그인 물결 무늬
  styles/         # tokens.css(디자인 토큰 — 수정은 여기서), fonts.css, index.css
  theme/          # tokens.ts(CSS 변수 → TS 이름 연결), theme.ts(MUI 테마)
  components/
    layout/       # AppLayout, Sidebar, PageHeader(상단바), UserMenu
    common/       # StatusChip, InfoList, StatCard, SectionTitle, TableCard, CodeBlock, CopyButton 등
    event/        # 신청 내용, 상태 처리 기록, 쿠폰 표, 화면 미리보기 (Console 재사용)
    notification/ # 알림 센터 (Console 재사용)
    report/       # 리포트 표 (Console 재사용)
  pages/          # 화면별 페이지 (events/apply = 신청 4단계, */components = 그 화면 전용)
  store/          # AppDataContext(임시 데이터 저장소 + 액션), ToastContext
  mocks/          # Mock 데이터(account, events, notifications, settings), 임시 단가(pricing)
  types/          # 도메인 타입 (PortalEvent, Coupon, StatusLog …)
  utils/          # status(상태 라벨·톤), format(숫자·기간·CSV), event(비용·플랜), validation
```

경로 별칭: `@/` = `src/` (예: `import StatusChip from '@/components/common/StatusChip'`)

---

## 개발 가이드

### 디자인 값은 `tokens.css`에서만

- 색·글자 크기·모서리·간격·컴포넌트 크기는 `src/styles/tokens.css`의 CSS 변수로만 정의합니다.
- 코드에서는 `@/theme/theme`에서 이름으로 가져다 씁니다(`color.point`, `text.secondary`, `radius.lg`, `size.control` …). **sx에 색·px 값을 직접 쓰지 않습니다.**
- 글자 크기는 Typography variant(`h2` 페이지 제목, `h3` 모달·빈 상태 제목, `h4` 카드 제목, `body1` 본문, `body2` 보조 문구, `caption`)로 맞춥니다.
- 상태 배지는 `StatusChip` + `utils/status.ts`의 `*StatusMeta`(라벨·톤)를 씁니다. 톤: 파랑(정보·대기) / 주황(처리 중·경고) / 초록(완료) / 빨강(반려·위험) / 회색(종료·실패).

### 디자인 — Console과 구분

같은 브랜드 팔레트(남색 + 페리윙클)를 씁니다. 사이드바는 **틀을 Console과 맞추고**(화면에 붙은 남색, 흰 TETRA 로고 + 영문 표기, 같은 여백·메뉴 크기), 선택 표시와 하단 무늬로 구분합니다.

| 항목 | Portal | Console |
| --- | --- | --- |
| 사이드바 | 남색 240px, 흰 로고 + 페리윙클 **PORTAL**, 메뉴 그룹 없음, 하단에 로그인 화면과 같은 **물결 무늬** | 남색 264px, 흰 로고 + 페리윙클 **CONSOLE**, 메뉴 그룹 제목, 하단 체커 무늬 |
| 활성 메뉴 | **흰 배경** + 남색 글자 + 페리윙클 아이콘 | 반투명 배경 + 페리윙클 바 |
| 상단바 | 흰색, 제목 위 작은 **영문 라벨**(EVENTS 등), 상위 경로는 제목 줄에 흐리게(`이벤트 내역 › 상세`) | 연한 페리윙클, 경로를 제목 위에 |
| 모서리 | **없음** — 카드·버튼·입력·배지·모달·점까지 모두 각지게 | 6 / 8 / 12 / 16px |
| 본문 | 흰 바탕 + 연회색 평면 카드 (Console과 같음) | 같음 |
| KPI 숫자 | 띠 없이 숫자만 | 네이비 띠 강조 |

- 포인트 색은 스타일 가이드 팔레트의 **페리윙클 `#86A1F3`** 입니다(`--color-point`가 `--color-peri`를 가리킴). 흰 배경 위 글자·아이콘에는 진한 페리윙클(`point-deep`), 페리윙클로 채운 면 위 글자에는 남색(`on-point`)을 씁니다.
- 남색 넓은 면적은 **사이드바와 로그인 좌측 패널만** 씁니다. 본문은 밝은 톤을 유지합니다.
- 포인트는 **영문 라벨, 현재 탭 밑줄, 현재 단계, 선택 상태, 입력 포커스 같은 작은 면적에만** 씁니다. 주요 버튼은 남색입니다.
- 모서리를 되살리려면 `tokens.css`의 `--radius-*`, `--sidebar-radius`만 바꾸면 됩니다.
- 결과 차트: 발급 성공 파랑 / 품절 주황 / 이탈 회색 (화면설계서 기준).

### 날짜·숫자 형식

- 화면 표시: `YYYY.MM.DD HH:mm` (mock·타입도 이 형식 문자열)
- 기간: `formatPeriod(start, end)` → `2026.09.20 00:00 ~ 09.27 23:59` (같은 해면 연도 생략)
- 숫자: `formatNumber` (천 단위 쉼표), 금액: `formatWon`
- 신청 폼의 `<input type="datetime-local">` 값(`YYYY-MM-DDTHH:mm`)은 제출할 때 `toDisplayDateTime`으로 바꿉니다(`pages/events/apply/draft.ts`).

### 새 화면 추가

1. `src/pages/<영역>/<이름>Page.tsx` 작성 — 맨 위에 `<PageHeader eyebrow="영문 라벨" breadcrumbs={[…]} />`
2. `src/App.tsx`의 `<AppLayout>` 아래에 `<Route>` 추가
3. 메뉴가 필요하면 `components/layout/Sidebar.tsx`의 `NAV`에 추가 (`to`가 없으면 비활성 "준비중")
4. 데이터·동작은 `AppDataContext`에 추가하고 화면은 `useAppData()`로만 접근

### Console과 같이 쓸 컴포넌트

Console의 신청 내용(OPS-03-2·04-2) · 알림센터 · 리포트는 "Portal 화면 재사용"입니다. 아래 컴포넌트는 데이터·동작을 props로만 받으므로 Console로 복사해 쓸 수 있습니다. **수정할 때는 Portal 전용 훅(`useAppData` 등)을 넣지 않습니다.**

- `components/event/ApplicationContent.tsx` (+ `StatusLogTimeline`, `CouponTable`) — 신청 내용
- `components/event/ScreenPreview.tsx` — 이벤트 화면 미리보기
- `components/notification/NotificationCenter.tsx` — 알림 센터
- `components/report/ReportTable.tsx` — 리포트 표

---

## API 연동

### 규칙

- API는 **같은 도메인의 상대 경로 `/api`**로 호출합니다. 빌드에 API 주소 같은 환경별 값을 넣지 않습니다(prod가 dev와 같은 이미지를 씀). 그래서 `.env`·`VITE_*` 환경 변수는 쓰지 않습니다.
- 로컬에서 `api/`와 함께 개발할 때는 `vite.config.ts`의 `server.proxy`에 `/api` → 로컬 API 포트를 추가합니다(API 포트가 정해지면 추가).

### 교체할 곳

`src/store/AppDataContext.tsx`가 mock 데이터를 메모리에 들고 있습니다. 화면은 이 Context만 쓰므로, 아래 액션과 조회 값을 API 호출로 바꾸면 됩니다.

| 액션 / 값 | 화면 |
| --- | --- |
| `login` | 로그인 — 실패 사유는 "계정 정보 없음" 1종 |
| `sendSupportMail` | 고객센터 문의 메일 |
| `setupPassword` (+ 토큰 검증) | 비밀번호 설정 |
| `events`, `getEvent`, `submitEvent` | 이벤트 목록·상세·신청 |
| `notifications`, `markNotificationRead` | 알림 센터 |
| `reports`, `sendReportEmail` | 리포트 |
| `settings`, `setEmailEnabled`, `connectSlack`, `disconnectSlack`, `saveSlack` | 설정 |

그 외 `TODO:` 위치:

- 배너 이미지 업로드 `pages/events/apply/InfoStep.tsx` · 썸네일 표시 `components/event/ApplicationContent.tsx`
- 발급 이력 CSV `pages/events/components/EventResultPanel.tsx`
- 감사 로그 파일 다운로드 `pages/reports/ReportPage.tsx`
- 로그아웃 `components/layout/UserMenu.tsx`, `pages/settings/SettingsPage.tsx`
- 플랜·호스팅 단가 `mocks/pricing.ts` · 전체 가이드 문서 주소 `pages/events/components/IntegrationGuide.tsx`(`GUIDE_DOC_URL`)
- 로그인 세션·라우트 보호 (아직 없음)

---

## 화면설계서 대비 차이

화면 구성·문구·동작은 **화면설계서(UI-TP-01 ~ 14)를 우선**합니다. 설계서 안에서 값이 서로 다르거나 정의가 없는 부분은 아래처럼 정했습니다. 확정되면 이 표를 갱신합니다.

| 항목 | 설계서 | 현재 구현 | 이유 |
| --- | --- | --- | --- |
| 트래픽 플랜 인원 | Tab2: S 1만 / M 3만 / L 5만 / XL 10만, 상세 예시: "M 플랜 (5만 명)" | Tab2 정의를 따름 | Tab2가 입력 정의, 상세는 예시 |
| 쿠폰 발급 매수 | 설명 ③ "쿠폰별 이름·Description·발급 매수", 그림 ⑤ 매수 입력칸 1개 | 쿠폰 행마다 발급 매수 + 총 발급 재고 합계 | 설명표 기준, 상세 화면(쿠폰별 매수 상이)과 일치 |
| 타임세일 · 라이브커머스 | 1차 오픈은 쿠폰만, 비활성화 여부 확인 필요 | 3종 모두 선택 가능, 쿠폰 입력은 쿠폰 타입일 때만 | 설계서 설명 기준 — 비활성화 확정 시 변경 |
| 추가 트래픽 산정 | Tab2 입력 항목 없음, 상세·Tab4에 표시 | Tab2 플랜 아래 체크박스 | 상세에 표시할 값의 입력 위치가 필요 |
| 호스팅 기간 검증 | 정의 없음 | 이벤트 기간을 모두 포함해야 다음 단계 가능 | 호스팅 중에만 이벤트 페이지 노출 |
| 고객센터 모달 | 비밀번호 문의용 이메일 작성 모달 | 받는 사람(고정) · 회신 받을 이메일 · 제목 · 내용 | 비로그인 상태라 회신 주소가 필요 — 확인 필요 |
| 로그인 입력 | 이메일 · 비밀번호 · [로그인] | 같음 (디자인 시안의 Remember me 토글은 넣지 않음) | 설계서 우선 |
| 상태 처리 기록 영문 표기 | 시안 예시 "신청됨 · Pending", "검증중 · Verifying" | 5종 상태에 Processing / Pending / Failed / Approved / Rejected | 설계서 상태 5종 기준 |
| 상세 [목록보기] 버튼 | 탭마다 채움/테두리 혼재 | 테두리 버튼으로 통일 | 보조 동작 |
| 단가 | "2026.09.21 기준 계획", 시간당 1,200원 예시 | `mocks/pricing.ts`의 임시 단가 | 가격 확정 후 교체 |
| 전체 가이드 문서 보기 | 새 창으로 열기 | 주소 미정 — 안내 토스트 | 문서 주소 확정 후 연결 |
| Slack 연동 해제 | 버튼만 정의 | 확인 모달 후 해제 | 되돌리기 어려운 동작 |
| 비밀번호 설정 (UI-TP-04) | 상세 설계 없음 | Console의 비밀번호 설정 화면과 같은 규칙(8자 이상 + 일치) | 문자 조합 규칙 미확정 |

---

## 폰트

- 본문: 프리젠테이션(Freesentation, SIL OFL) — noonnu CDN
- 제목: 페이퍼로지(Paperlogy, SIL OFL) — noonnu CDN
- 굵기는 역할별 토큰(`tokens.css`의 `--fw-*`, `--fw-heading-*`)으로 씁니다.
