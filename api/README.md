# Tetra Portal API

고객사(테넌트) 관리자 화면(`../web`)이 부르는 API. Spring Boot 4.1 · Java 21 · JPA · MySQL 8.4.
계획 · 기능 목록 · 단계는 팀 문서 `MVP/WEB/PLAN&DATA/MVP_tetraportal_plan.md`, 인프라와의 약속은 `Portal_Console_백엔드_인프라_약속.md`.

| 단계 | 내용 | 상태 |
|---|---|---|
| P0 | 뼈대 — 설정 · 오류 형식 · 스키마 멀티테넌시 · 화면 서빙 · 헬스 체크 · 시험 · 로컬 실행 | ✅ |
| P1 ~ P6 | 인증 → 조회 → 신청(WF1-1) → 결과 → 부가 → 배포 | 예정 |

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
cp .env.example .env              # DB_PASSWORD · LOCAL_MYSQL_ROOT_PASSWORD 에 아무 값
docker compose up -d --wait       # MySQL 3307 — 스키마 · 앱 계정 · seed(요금제 · 테넌트 2개)를 첫 기동 때 만든다
./gradlew bootRun                 # .env 값을 환경 변수로 넣어 띄운다 → http://localhost:8082
```

| 확인 | 기대 |
|---|---|
| `curl localhost:8082/actuator/health` | `{"status":"UP"}` (DB 포함) |
| `curl localhost:8082/api/portal/me` | 401 `UNAUTHENTICATED` (로그인은 P1) |
| `http://localhost:8082/swagger-ui.html` | API 문서 (`API_DOCS_ENABLED=true`일 때만) |

- 포트: 로컬은 **8082**(issuance-api 로컬이 8080 · 8081을 쓴다). 운영(ECS)은 8080.
- 로컬 테넌트: `k7f2m9qx4b1t`(테넌트컴퍼니, `admin@tenant-company.com`) · `d4n8p2r6s0v1` — 비밀번호는 아직 없다(P1).
- 데이터를 처음부터 다시: `docker compose down -v` → `up`.
- 화면과 함께: `../web`에서 `npm run build` 뒤 `./gradlew bootRun`이면 `web/dist`가 같이 서빙된다. 화면 개발 서버(`npm run dev`, 5174)는 `vite.config.ts`의 `server.proxy`로 `/api` → `http://localhost:8082`.

## 구조

```
src/main/java/com/tetra/portal/
├─ config/        설정 · 보안 · 화면 서빙(SpaWebConfig) · API 문서 · 멀티테넌시 연결
├─ common/        오류 형식(error/) · KST 시간(time/) · DB 오류 판별
├─ tenancy/       스키마 라우팅 — TenantRouter.inTenant()만 테넌트 스키마로 들어간다
└─ domain/        엔티티 — platform/(tetra_platform) · tenant/(t_{tenant_id}, P2부터)
```

### 지킬 것 (Console API와 같은 규칙)

- **테넌트 스키마는 `TenantRouter.inTenant(tenantId, …)` 안에서만**, 그리고 **트랜잭션 밖에서** 부른다(안에서 부르면 예외). tenant_id는 로그인 정보(JWT)에서만 가져온다 — 요청 경로 · 본문 값으로 라우팅하지 않는다.
- 테넌트 엔티티는 `TenantScopedEntity`를 상속한다 → 읽은 행의 `tenant_id`가 라우팅한 테넌트와 다르면 500(이중 확인).
- 스키마를 바꾸지 않는다(`ddl-auto=none`, 계정도 DML만). 열이 필요하면 db-schema 저장소 MR.
- 응답 일시는 ISO `+09:00`, 상태 값은 DB 값 그대로, 오류는 `ErrorResponse {code, message, fields?}`.
- 비밀 값은 환경 변수로만 받고 로그 · 오류 메시지에 넣지 않는다. `.env`는 커밋하지 않는다.
