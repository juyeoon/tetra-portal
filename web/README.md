# Tetra Portal — 화면 (web)

React 19 + TypeScript + Vite 7 + MUI 7 (+ MUI X Charts) · React Router 7

운영 Console(`tetra/app/console`의 `web/`)의 구조와 디자인 토큰을 복사해서 시작했습니다. 공통 라이브러리로 묶지 않았으므로 두 저장소는 각각 수정합니다. 저장소 전체 안내(브랜치·커밋·MR 규칙, GitLab 접속)는 [루트 README](../README.md)를 보세요.

> 모든 화면이 Portal API(`/api/portal`, 같은 이미지의 `api/`)를 부릅니다. 화면 안에 들고 있는 mock 데이터는 없습니다 — 로컬에서는 `api/`의 로컬 시드 데이터로 확인합니다.

---

## 실행

화면은 `/api` 요청을 로컬 Portal API(`http://localhost:8082`)로 넘깁니다(`vite.config.ts` proxy). API를 먼저 띄웁니다 — 방법은 [api/README](../api/README.md).

```bash
# 1) API (portal/api) — MySQL(docker compose, 3307) + ./gradlew bootRun (8082)
# 2) 화면
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
- API가 꺼져 있으면 로그인 화면에 "서버에 연결하지 못했습니다" 안내가 뜹니다.

---

## 테스트 계정

로컬 API의 시드 테넌트(`api/docker/local/seed`)를 씁니다. 처음에는 비밀번호가 없으므로 **비밀번호 설정 링크**를 만들어 설정한 뒤 로그인합니다.

```powershell
# portal/api 에서 — 출력된 주소를 브라우저로 열기 (기본 5174 화면 주소)
.\docker\local\password-link.ps1                          # k7f2m9qx4b1t  admin@tenant-company.com
.\docker\local\password-link.ps1 -TenantId d4n8p2r6s0v1   # owner@second-shop.com
```

| 확인 | 방법 | 결과 |
| --- | --- | --- |
| 비밀번호 설정 (UI-TP-04) | 링크 열기 → 8~64자 입력 | 저장 → 로그인 화면 |
| 링크 재사용 · 만료 | 같은 링크 다시 열기 | "링크가 만료되었거나 이미 사용되었습니다" |
| 로그인 (UI-TP-01) | 설정한 이메일 · 비밀번호 | `/events` (또는 로그인 전에 열려던 주소) |
| 로그인 실패 (UI-TP-02) | 틀린 이메일 또는 비밀번호 | 하단 "로그인 실패" 배너 |
| 라우트 보호 | 로그아웃 상태에서 `/events` 열기 | 로그인 화면 → 로그인하면 원래 주소로 |
| 로그아웃 | 우측 상단 메뉴 또는 설정 화면 | 로그인 화면, 이후 `/events`는 다시 로그인 화면 |
| 고객센터 (UI-TP-03) | 로그인 화면 → 고객센터 | 로컬은 `api/build/local-mail/`에 메일 파일 저장 |

로그인 쿠키(`TETRA_PORTAL_TOKEN`, 8시간)는 HttpOnly라 화면 코드가 읽지 않습니다. 새로고침하면 `/me`로 로그인 상태를 다시 확인합니다.

---

## 테스트 시나리오

로컬 시드(`api/docker/local/seed/local_seed.sql`)의 테넌트 `k7f2m9qx4b1t`(`admin@tenant-company.com`)로 로그인해서 확인합니다. 시드를 처음 상태로 되돌리려면 `api/`에서 `docker compose down -v` 후 다시 띄웁니다.

### 이벤트 (`/events/:eventId`)

| 이벤트 ID | 이벤트명 | 신청 / 호스팅 / 이벤트 상태 | 확인할 것 |
| --- | --- | --- | --- |
| 1003 | 추석 감사 쿠폰 | 승인됨 / 호스팅 종료 / 만료 | **결과 탭** 지표 · 차트 · CSV 다운로드(로컬은 받을 수 없는 가짜 주소), 진행 기록 7건 |
| 1002 | 가을 정기 세일 쿠폰 | 승인됨 / 배포 대기 / 오픈 예정 | 결과 탭 비활성(툴팁), 사전 가이드에 실제 도메인 |
| 1001 | 블랙프라이데이 쿠폰 | 승인 대기 중 / - / - | 도메인 "승인 후 발급" |
| 1004 | 연말 감사 쿠폰 | 반려됨 / - / - | 상태 처리 기록의 **반려 사유** |
| 1005 | 신규 가입 쿠폰 | 신청 실패 / - / - | 상태 처리 기록의 실패 **원인**, 배너 "등록되지 않음" |

- 상세 탭은 주소로 바로 열 수 있습니다: `?tab=application` (신청 내용) · `?tab=guide` (사전 가이드) · `?tab=result` (결과 — 진행 전 · 진행 중이면 신청 내용으로)
- 없는 ID · 다른 테넌트의 ID로 들어가면 "이벤트를 찾을 수 없습니다" 화면이 나옵니다.

### 이벤트 신청 (`/events/new`)

아래 값으로 4단계를 끝까지 진행하면 신청이 접수(202)되고 상세로 이동합니다. 처음에는 "신청됨(처리 중)", 배너 업로드가 끝나면(로컬은 바로) "승인 대기 중"입니다.

| 단계 | 입력 예시 | 확인할 것 |
| --- | --- | --- |
| 1. 이벤트 타입 | 쿠폰 [선택] | 타임세일 · 라이브커머스는 "준비 중"(템플릿이 쿠폰만 있음 — 서버도 거절) |
| 2. 이벤트 정보 | 이름 `12월 회원 감사 쿠폰` · 이벤트 `2026-12-01 10:00 ~ 12:00` · 호스팅 `2026-12-01 09:00 ~ 13:00` · 배너 PNG/JPG 2MB 이하 · 쿠폰 2행 · 총 발급 재고 `3,000` · 플랜 M · URL `https://shop.example.com/tetra-entry` → [확인] | 모든 필수 항목 + URL 확인 전에는 [다음 단계] 비활성. 쿠폰 행 11번째 추가 시 경고 모달. 호스팅 시작이 지금 이전이거나 이벤트 기간을 벗어나면 오류. 2MB 넘는 파일 · GIF → 오류 |
| 3. 화면 설정 | 위젯 위치 · 배경 테마 변경 | 오른쪽 미리보기에 바로 반영, 상세의 "이벤트 화면 설정"에 같은 값 |
| 4. 신청서 제출 | [이벤트 미리보기] → [신청하기] | 금액 표시 없음. 서버가 거절하면(400) 항목별 사유 목록 + [이벤트 정보 수정하기] |

### 그 밖의 화면

| 화면 | 확인할 것 |
| --- | --- |
| 리포트 `/reports` | [다운로드] → 5분짜리 다운로드 링크(로컬은 가짜 주소), [이메일 전송] → 로그인 계정 주소로 전송 토스트(로컬은 `api/build/local-mail/`) |
| 알림 센터 `/notifications` | 안읽음 건 — 항목 클릭 시 읽음(새로고침해도 유지), 필터 5종 건수, 검색 즉시 반영, Slack "준비 중" |
| 설정 `/settings` | 이메일 수신 토글(새로고침해도 유지), Slack "준비 중" |
| 사용자 메뉴 (우측 상단) | 설정 이동, 로그아웃 → `/login` |

---

## 화면 / 라우트

| 화면 | Screen ID | 경로 | 파일 |
| --- | --- | --- | --- |
| 로그인 · 로그인 실패 | UI-TP-01 · 02 | `/login` | `pages/auth/LoginPage.tsx` |
| 고객센터 (모달) | UI-TP-03 | 로그인 화면 링크 | `pages/auth/components/SupportMailDialog.tsx` |
| 비밀번호 설정 | UI-TP-04 | `/password/setup?t=&exp=&sig=` | `pages/auth/PasswordSetupPage.tsx` |
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
  api/            # client(fetch · 오류 형식 · 401 처리), auth(로그인 · 비밀번호 · 문의), portal(그 밖의 API), useApi(화면 데이터 불러오기 훅)
  assets/         # 로고(남색·흰색), 로그인 물결 무늬
  styles/         # tokens.css(디자인 토큰 — 수정은 여기서), fonts.css, index.css
  theme/          # tokens.ts(CSS 변수 → TS 이름 연결), theme.ts(MUI 테마)
  components/
    layout/       # AppLayout, Sidebar, PageHeader(상단바), UserMenu, RequireAuth(로그인 확인)
    common/       # StatusChip, InfoList, StatCard, SectionTitle, TableCard, CodeBlock, CopyButton, LoadState(불러오는 중 · 오류) 등
    event/        # 신청 내용, 상태 처리 기록, 쿠폰 표, 화면 미리보기 (Console 재사용)
    notification/ # 알림 센터 (Console 재사용)
    report/       # 리포트 표 (Console 재사용)
  pages/          # 화면별 페이지 (events/apply = 신청 4단계, */components = 그 화면 전용)
  store/          # AuthContext(로그인 상태 · useAccount), ToastContext
  mocks/          # 화면 고정 값(고객센터 주소 표시, 버전)
  types/          # API 응답 모양 그대로 (EventSummary, EventDetail, EventResult …)
  utils/          # status(상태 라벨·톤), format(일시 · 숫자 · 기간), event(플랜 표시 · 일시 변환), validation
```

경로 별칭: `@/` = `src/` (예: `import StatusChip from '@/components/common/StatusChip'`)

---

## 개발 가이드

### 디자인 값은 `tokens.css`에서만

- 색·글자 크기·모서리·간격·컴포넌트 크기는 `src/styles/tokens.css`의 CSS 변수로만 정의합니다.
- 코드에서는 `@/theme/theme`에서 이름으로 가져다 씁니다(`color.point`, `text.secondary`, `radius.lg`, `size.control` …). **sx에 색·px 값을 직접 쓰지 않습니다.**
- 글자 크기는 Typography variant(`h2` 페이지 제목, `h3` 모달·빈 상태 제목, `h4` 카드 제목, `body1` 본문, `body2` 보조 문구, `caption`)로 맞춥니다.
- 상태 배지는 `StatusChip` + `utils/status.ts`의 `*StatusMeta`(라벨·톤)를 씁니다. 톤: 파랑(정보·대기) / 주황(처리 중·경고·확인 중) / 초록(완료) / 빨강(반려) / 회색(종료·신청 실패).
- 호스팅·이벤트 칸은 `eventChipsOf`로 정합니다. 배포·호스팅 시작·이벤트 준비·종료 실패와, 예정 시각이 지났는데 진행되지 않은 상태(호스팅 시작 시각이 지난 배포 대기·배포 완료, 입장 시각이 지난 오픈 예정)는 모두 "확인 중"(주황) + "운영팀이 확인하고 있습니다."로 보여 주고 실패·원인은 보여 주지 않습니다. 콘솔은 실패를 그대로 보여 줍니다.

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

- API 일시는 ISO-8601 `+09:00` 문자열입니다(`2026-11-27T20:00:00+09:00`). 타입도 이 문자열 그대로 두고, **그릴 때** `formatDateTime`(`YYYY.MM.DD HH:mm`) · `formatDate` · `formatTime`으로 바꿉니다. 서버가 항상 한국 시각으로 보내므로 브라우저 시간대와 상관없이 글자를 잘라 씁니다.
- 기간: `formatPeriod(formatDateTime(start), formatDateTime(end))` → `2026.09.20 00:00 ~ 09.27 23:59` (같은 해면 연도 생략)
- 숫자: `formatNumber` (천 단위 쉼표), 비율(0~1): `formatRate`. **금액은 화면에 표시하지 않습니다**(계획서 Q3 — API에도 없음).
- 신청 폼의 `<input type="datetime-local">` 값(`YYYY-MM-DDTHH:mm`, 한국 시각)은 보낼 때 `toApiDateTime`으로 `+09:00`을 붙입니다(`pages/events/apply/draft.ts`).

### 새 화면 추가

1. `src/pages/<영역>/<이름>Page.tsx` 작성 — 맨 위에 `<PageHeader eyebrow="영문 라벨" breadcrumbs={[…]} />`
2. `src/App.tsx`의 `<AppLayout>` 아래에 `<Route>` 추가
3. 메뉴가 필요하면 `components/layout/Sidebar.tsx`의 `NAV`에 추가 (`to`가 없으면 비활성 "준비중")
4. API 호출은 `src/api/portal.ts`에 함수로 추가하고, 화면에서는 `useApi(함수, deps)`로 불러옵니다(불러오는 중 · 오류는 `LoadingView` · `ErrorView`). 로그인 계정은 `useAccount()`

### Console과 같이 쓸 컴포넌트

Console의 신청 내용(OPS-03-2·04-2) · 알림센터 · 리포트는 "Portal 화면 재사용"입니다. 아래 컴포넌트는 데이터·동작을 props로만 받으므로 Console로 복사해 쓸 수 있습니다. **수정할 때는 Portal 전용 훅(`useApi`, `useAccount` 등)을 넣지 않습니다.**

- `components/event/ApplicationContent.tsx` (+ `StatusLogTimeline`, `CouponTable`) — 신청 내용
- `components/event/ScreenPreview.tsx` — 이벤트 화면 미리보기
- `components/notification/NotificationCenter.tsx` — 알림 센터
- `components/report/ReportTable.tsx` — 리포트 표

---

## API 연동

### 규칙

- API는 **같은 도메인의 상대 경로 `/api/portal`**로 호출합니다. 빌드에 API 주소 같은 환경별 값을 넣지 않습니다(prod가 dev와 같은 이미지를 씀). 그래서 `.env`·`VITE_*` 환경 변수는 쓰지 않습니다.
- 로컬은 `vite.config.ts`의 `server.proxy`가 `/api` → `http://localhost:8082`(로컬 API)로 넘깁니다.
- 로그인은 HttpOnly 쿠키(`TETRA_PORTAL_TOKEN`, 8시간)라 화면 코드가 토큰을 다루지 않습니다. 어떤 API든 401이 오면 로그인 화면으로 갑니다(`api/client.ts` → `AuthContext`).
- 오류는 서버 형식 `{ code, message, fields? }`을 `ApiError`로 받습니다. `message`는 화면에 그대로 보여도 되는 문구입니다. 오류 토스트는 `toast(errorMessage(e), 'error')`.
- API 문서: 로컬 API를 띄우고 `http://localhost:8082/swagger-ui.html`

### 화면 ↔ API

| 화면 | API (`/api/portal` 아래) |
| --- | --- |
| 로그인 · 로그아웃 · 로그인 확인 | `POST /auth/login` · `POST /auth/logout` · `GET /me` |
| 비밀번호 설정 | `POST /password/setup/verify` → `POST /password/setup` |
| 고객센터 문의 | `POST /support-mail` (발송 실패 503 — 창을 닫지 않음) |
| 이벤트 목록 · 상세 | `GET /events` · `GET /events/{id}` (+ 플랜 표시 `GET /plans`) |
| 이벤트 신청 | `GET /plans` · `POST /events` (multipart: `application` JSON + `banner` 파일) |
| 결과 탭 | `GET /events/{id}/result` · CSV `GET /events/{id}/result/history-csv` (다운로드 링크) |
| 알림 센터 | `GET /notifications` · `PATCH /notifications/{id}/read` (+ `GET /settings`) |
| 리포트 | `GET /reports` · `GET /reports/{id}/download` · `POST /reports/{id}/email` |
| 설정 | `GET /settings` · `PATCH /settings/email` |

남은 `TODO:` — 전체 가이드 문서 주소 `pages/events/components/IntegrationGuide.tsx`(`GUIDE_DOC_URL`)

---

## 화면설계서 대비 차이

화면 구성·문구·동작은 **화면설계서(UI-TP-01 ~ 14)를 우선**합니다. 설계서 안에서 값이 서로 다르거나 정의가 없는 부분은 아래처럼 정했습니다. 확정되면 이 표를 갱신합니다.

| 항목 | 설계서 | 현재 구현 | 이유 |
| --- | --- | --- | --- |
| 트래픽 플랜 인원 | Tab2: S 1만 / M 3만 / L 5만 / XL 10만, 상세 예시: "M 플랜 (5만 명)" | Tab2 정의를 따름 | Tab2가 입력 정의, 상세는 예시 |
| 쿠폰 발급 매수 | 설명 ③ "쿠폰별 이름·Description·발급 매수", 그림 ⑤ 매수 입력칸 1개 | 쿠폰 행은 이름 · Description만, **총 발급 재고 1칸** | DB 재고가 이벤트 단위 1개(일괄 발급, Portal 계획서 5-5) |
| 타임세일 · 라이브커머스 | 1차 오픈은 쿠폰만, 비활성화 여부 확인 필요 | "준비 중" — 선택 불가 | 템플릿이 쿠폰만 있어 서버가 거절 (`draft.ts`의 `AVAILABLE_TYPES`) |
| 추가 트래픽 산정 · 배너 크기 | 상세 · Tab4에 표시 | 표시 · 입력 없음 | DB에 열이 없어 1차 제외 (Portal 계획서 5-6 · Q2) |
| 호스팅 기간 검증 | 정의 없음 | 이벤트 기간을 모두 포함 + 시작이 지금 이후여야 다음 단계 가능 | 호스팅 중에만 이벤트 페이지 노출, 서버와 같은 규칙 |
| 고객센터 모달 | 비밀번호 문의용 이메일 작성 모달 | 받는 사람(고정) · 회신 받을 이메일 · 제목 · 내용 | 비로그인 상태라 회신 주소가 필요 — 확인 필요 |
| 로그인 입력 | 이메일 · 비밀번호 · [로그인] | 같음 (디자인 시안의 Remember me 토글은 넣지 않음) | 설계서 우선 |
| 상태 처리 기록 영문 표기 | 시안 예시 "신청됨 · Pending", "검증중 · Verifying" | 5종 상태에 Processing / Pending / Failed / Approved / Rejected | 설계서 상태 5종 기준 |
| 상세 [목록보기] 버튼 | 탭마다 채움/테두리 혼재 | 테두리 버튼으로 통일 | 보조 동작 |
| 단가 · 예상 비용 | "2026.09.21 기준 계획", 시간당 1,200원 예시 · Tab4 예상 비용 | **표시하지 않음** (플랜은 수용 인원만) | 금액 비공개 결정 (Portal 계획서 Q3) — DB에는 신청 때 기록 |
| 전체 가이드 문서 보기 | 새 창으로 열기 | 주소 미정 — 안내 토스트 | 문서 주소 확정 후 연결 |
| 사전 가이드 JWT (UI-TP-6-1) | HS256 · 연동키 · `eventId` · `sub` · `GET /session?token=` | **RS256 · 헤더 `kid`(승인 메일의 키 버전) · `user_id` · `tenant_id` · `event_id`(숫자) · `GET /api/issuance/session?JWT=`**, 유효 최대 5분 | 실제 발급 API(issuance-api `JwtAuthFilter`) 규칙 (Portal 계획서 5-9) |
| Slack 연동 (UI-TP-13 · 14) | 워크스페이스 · 채널 등록 · 연동 해제 | "준비 중" 안내만 | MVP 1차 미제공(`slackAvailable: false`) |
| 이벤트 배너 썸네일 | 상세에 이미지 | 자리 표시 + 등록 상태 문구 | 상세 응답에 이미지 주소 없음 |
| 상태 처리 기록 | 신청 상태 5종 | 신청 · 호스팅 · 이벤트 상태 변화 모두, 처리 주체(테넌트 관리자 · 플랫폼 운영자 · 자동 처리) | API 진행 기록 기준, 문구는 Console과 같음 |
| 비밀번호 설정 (UI-TP-04) | 상세 설계 없음 | Console의 비밀번호 설정 화면과 같은 규칙(8자 이상 + 일치) | 문자 조합 규칙 미확정 |

---

## 폰트

- 본문: 프리젠테이션(Freesentation, SIL OFL) — noonnu CDN
- 제목: 페이퍼로지(Paperlogy, SIL OFL) — noonnu CDN
- 굵기는 역할별 토큰(`tokens.css`의 `--fw-*`, `--fw-heading-*`)으로 씁니다.
