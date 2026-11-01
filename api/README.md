# Tetra Portal API

고객사(테넌트) 관리자 화면(`../web`)이 부르는 API. Spring Boot 4.1 · Java 21 · JPA · MySQL 8.4.
계획 · 기능 목록 · 단계는 팀 문서 `MVP/WEB/PLAN&DATA/MVP_tetraportal_plan.md`, 인프라와의 약속은 `Portal_Console_백엔드_인프라_약속.md`.

| 단계 | 내용 | 상태 |
|---|---|---|
| P0 | 뼈대 — 설정 · 오류 형식 · 스키마 멀티테넌시 · 화면 서빙 · 헬스 체크 · 시험 · 로컬 실행 | ✅ |
| P1 | 인증 — 로그인 · 로그아웃 · 계정 정보 · 비밀번호 설정(Console 링크) | ✅ |
| P2 | 조회 — 요금제 · 이벤트 목록 · 상세 · 테넌트 격리 | ✅ |
| P3 | 신청(WF1-1) — 검사 · 접수(202) · 배너 비동기 업로드 · APPLIED / APPLY_FAILED · 테넌트 자동 활성화 · 멈춘 신청 정리 | ✅ |
| P4 | 결과 — 결과 탭(판정 · 지표 · 분 단위 추이) · 발급 이력 CSV 다운로드 링크(presigned URL) | ✅ |
| P5 | 부가 — 알림 · 리포트(링크 · 안내 메일) · 이메일 알림 설정 · 문의 메일 | ✅ (메일은 운영 SES 권한 P6) |
| P6 | 배포 — CI · 35-web MR · dev 첫 배포 | 예정 |

## API (지금까지)

| API | 인증 | 설명 |
|---|---|---|
| `POST /api/portal/auth/login` `{email, password}` | 공개 | 성공 → 쿠키 `TETRA_PORTAL_TOKEN`(HttpOnly · Secure · SameSite=Strict · Path=/api/portal · 8시간) + `{tenantId, companyName, managerName, email}`. 실패는 이유와 상관없이 401 `LOGIN_FAILED` |
| `POST /api/portal/auth/logout` | 공개 | 쿠키 삭제, 204 |
| `GET /api/portal/me` | 쿠키 | 로그인한 계정(위와 같은 모양) |
| `POST /api/portal/password/setup/verify` `{t, exp, sig}` | 공개 | 메일 링크 확인 → `{email}`. 만료 · 사용됨 · 위조는 모두 410 `PASSWORD_LINK_INVALID` |
| `POST /api/portal/password/setup` `{t, exp, sig, password}` | 공개 | 비밀번호 8~64자 → 204. 같은 링크는 한 번만 |
| `GET /api/portal/plans` | 쿠키 | 노출된 요금제(작은 것부터) `{planCode, maxCapacity}` — **금액은 내보내지 않는다**(2026-10-19) |
| `GET /api/portal/events` | 쿠키 | 내 이벤트 목록(최근 신청 먼저) — 상태 3종 · 기간 · 요금제 |
| `POST /api/portal/events` (multipart) | 쿠키 | 신청 — `application` 파트(JSON, **Content-Type: application/json**) + `banner` 파트(PNG · JPEG ≤ 2MB) → 202 `{eventId, applicationStatus: APPLYING}`. 배너가 올라가면 APPLIED, 실패하면 APPLY_FAILED(원인은 진행 기록) — 상세로 확인. 검사 실패는 400 `fields`에 항목별 사유 |
| `GET /api/portal/events/{eventId}/result` | 쿠키 | 결과 탭 — `state`(READY · AGGREGATING · IN_PROGRESS · END_FAILED)에 따라 지표 · 구성 · 분 단위 추이 / 실패 원인. Console 결과와 같은 판정 · 숫자 |
| `GET /api/portal/events/{eventId}/result/history-csv` | 쿠키 | `{url, expiresAt}` — S3 presigned URL(5분). 열면 `issuance-history-{id}.csv`로 저장. READY 아니면 409 `RESULT_NOT_READY`. 로컬은 받을 수 없는 가짜 주소 |
| `GET /api/portal/notifications?priority&chip&unreadOnly&q&page&size` | 쿠키 | 알림센터 한 페이지 — 최신순(같은 시각은 나중 알림 먼저), 조건은 모두 AND. `priority` URGENT · WARNING · INFO, `chip` APPLICATION · EVENT · REPORT(없으면 전체), `unreadOnly`(기본 false), `q` 내용 부분 일치(100자 이하), `page` 1부터(10000 이하), `size` 1~50(기본 10) — 범위 밖 값은 400 `fields`. 응답 `{items, page, size, totalCount, totalPages, unreadCount, counts: {priority, chip}}` — 우선순위 건수는 안 읽은 것만만, 분류 건수는 우선순위 · 안 읽은 것만을 따른다(고른 분류 · 검색과 무관). 범위 밖 page는 빈 `items` |
| `GET /api/portal/notifications/unread-count` | 쿠키 | 사이드바 배지 `{count}` — 안 읽은 알림 수(인덱스 `(is_read, occurred_at)`로 센다). 목록 응답 `unreadCount`와 같은 메서드 |
| `PATCH /api/portal/notifications/{id}/read` · `PATCH …/read-all` | 쿠키 | 읽음 표시(204, 이미 읽었어도) · 모두 읽음 — 그 테넌트의 안 읽은 알림 전부(204, 없어도) |
| `GET /api/portal/reports` · `GET …/{id}/download` · `POST …/{id}/email` | 쿠키 | 리포트 목록 · 다운로드 링크(presigned 5분) · 로그인 이메일로 안내 메일(204, SES 실패 503) |
| `GET /api/portal/settings` · `PATCH …/email {enabled}` | 쿠키 | `{emailEnabled, slackAvailable: false}` |
| `POST /api/portal/support-mail` `{from, subject, body}` | 공개 | 운영팀 주소(`MAIL_SUPPORT_TO`)로만, Reply-To = from → 204 / 503 |
| `GET /api/portal/events/{eventId}` | 쿠키 | 상세 — 신청 내용 · 쿠폰 · 화면 구성(`layout` JSON) · 도메인 `{slug}.{tenant_id}.{EVENT_ROOT_DOMAIN}`(승인 후, 만료 전 — 기본 `tetraops.cloud`, 테넌트 `root_domain`은 고객사 도메인이라 쓰지 않음) · 진행 기록(`actor`: TENANT · OPERATOR · SYSTEM). 다른 테넌트 이벤트는 404 |

- 비밀번호 설정 링크는 Console이 만든다: `{Portal}/password/setup?t={tenant_id}&exp={epoch초}&sig={서명}`. 화면은 주소의 세 값을 그대로 본문에 담아 보낸다(확인도 POST — 서명이 접근 로그에 한 번 더 남지 않게).
- 상태를 바꾸는 API는 JSON 본문만 받는다(그 밖은 415). CSRF 토큰 대신 SameSite=Strict 쿠키 + JSON 전용.
- 전체 문서: 로컬에서 `http://localhost:8082/swagger-ui.html`.

---

## 시험

```bash
./gradlew test
```

- Docker가 켜져 있어야 한다(Testcontainers가 MySQL 8.4를 띄운다).
- 스키마는 `db/migration`(db-schema 저장소 사본 — `db/README.md`)으로 공용 1개 + 테넌트 2개(`t_aaaaaaaaaaaa`, `t_bbbbbbbbbbbb`)를 만들고, 앱은 **DML만 있는 계정**으로 붙는다(dev RDS와 같은 권한).
- 커넥션 풀을 1개로 두어 테넌트 스키마가 다음 요청에 남지 않는지도 함께 확인한다.

## 로컬 실행

```bash
cp .env.example .env              # 빈 값에 아무 값 (PORTAL_JWT_SECRET은 32자 이상)
docker compose up -d --wait       # MySQL 3307 — 스키마 · 앱 계정 · seed(요금제 · 테넌트 2개)를 첫 기동 때 만든다
./gradlew bootRun                 # .env 값을 환경 변수로 넣어 띄운다 → http://localhost:8082
```

| 확인 | 기대 |
|---|---|
| `curl localhost:8082/actuator/health` | `{"status":"UP"}` (DB 포함) |
| `curl localhost:8082/api/portal/me` | 401 `UNAUTHENTICATED` (로그인 전) |
| `http://localhost:8082/swagger-ui.html` | API 문서 (`API_DOCS_ENABLED=true`일 때만) |

- 로컬은 profile `local`(`.env`의 `SPRING_PROFILES_ACTIVE=local`) — AWS를 부르지 않는다. 배너는 `build/local-banners/{tenant_id}/assets/banners/{event_id}/`, 메일은 `build/local-mail/*.txt`에 파일로, 다운로드 링크는 받을 수 없는 가짜 주소.
- 포트: 로컬은 **8082**(issuance-api 로컬이 8080 · 8081을 쓴다). 운영(ECS)은 8080.
- 로컬 테넌트: `k7f2m9qx4b1t`(테넌트컴퍼니, `admin@tenant-company.com`) · `d4n8p2r6s0v1`(`owner@second-shop.com`) — 처음엔 비밀번호가 없다(Console 가입 직후와 같음). 아래로 설정한다.
- 테넌트컴퍼니에는 상태별 샘플 이벤트 5건이 있다: 1001 신청 완료 · 1002 승인(배포 대기, 도메인 있음) · 1003 승인 후 종료(결과 · CSV 있음) · 1004 반려(사유 있음) · 1005 신청 실패. seed를 바꿨으면 `docker compose down -v` → `up`(로컬 비밀번호도 지워진다).

### 로컬에서 비밀번호 설정 · 로그인

Console 없이 "가입 메일 링크"를 만든다(같은 서명 규칙, `.env`의 `PASSWORD_LINK_HMAC_SECRET` 사용).

```powershell
.\docker\local\password-link.ps1                                  # 화면 개발 서버(5174)용 링크 — 브라우저로 열기
.\docker\local\password-link.ps1 -TenantId d4n8p2r6s0v1 -BaseUrl http://localhost:8082
```

링크로 비밀번호를 정한 뒤 그 이메일 · 비밀번호로 로그인한다. 링크는 한 번만 쓸 수 있다(다시 하려면 스크립트를 다시 실행).
- 데이터를 처음부터 다시: `docker compose down -v` → `up`.
- 화면과 함께: `../web`에서 `npm run build` 뒤 `./gradlew bootRun`이면 `web/dist`가 같이 서빙된다. 화면 개발 서버(`npm run dev`, 5174)는 `vite.config.ts`의 `server.proxy`로 `/api` → `http://localhost:8082`.

## 구조

```
src/main/java/com/tetra/portal/
├─ auth/          로그인 · JWT 쿠키 · 로그인한 테넌트(CurrentTenant)
├─ account/       비밀번호 설정(Console 링크 검증)
├─ config/        설정 · 보안 · 화면 서빙(SpaWebConfig) · API 문서 · 멀티테넌시 연결
├─ common/        오류 형식(error/) · KST 시간(time/) · DB 오류 판별
├─ tenancy/       스키마 라우팅 — TenantRouter.inTenant()만 테넌트 스키마로 들어간다
├─ plan/          요금제
├─ event/         이벤트 목록 · 상세 (EventReader = 테넌트 스키마 읽기)
│  └─ apply/      신청 WF1-1 — 검사(ApplyService) · 트랜잭션 a/b/c(ApplyWriter) · 비동기 업로드(BannerUploader) · 정리(StaleApplicationSweeper)
├─ result/        결과 탭 · 발급 이력 CSV 링크
├─ notification/  알림 · report/ 리포트 · settings/ 이메일 알림 · support/ 문의 메일
├─ external/      배너 저장 · 감사 버킷 링크(AuditFileLinks) · 메일 — S3 · SES(운영) / 로컬 대체(local)
└─ domain/        엔티티 — platform/(tetra_platform) · tenant/(t_{tenant_id})
```

### 지킬 것 (Console API와 같은 규칙)

- **테넌트 스키마는 `TenantRouter.inTenant(tenantId, …)` 안에서만**, 그리고 **트랜잭션 밖에서** 부른다(안에서 부르면 예외). tenant_id는 로그인 정보(`CurrentTenant.id(jwt)`)에서만 가져온다 — 요청 경로 · 본문 값으로 라우팅하지 않는다.
- 테넌트 엔티티는 `TenantScopedEntity`를 상속한다 → 읽은 행의 `tenant_id`가 라우팅한 테넌트와 다르면 500(이중 확인).
- 스키마를 바꾸지 않는다(`ddl-auto=none`, 계정도 DML만). 열이 필요하면 db-schema 저장소 MR.
- 응답 일시는 ISO `+09:00`, 상태 값은 DB 값 그대로, 오류는 `ErrorResponse {code, message, fields?}`.
- 비밀 값은 환경 변수로만 받고 로그 · 오류 메시지에 넣지 않는다. `.env`는 커밋하지 않는다.
