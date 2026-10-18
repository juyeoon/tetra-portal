# Tetra Portal

고객사(테넌트) 관리자가 이벤트를 신청하고, 진행 상태·결과·연동 가이드를 확인하는 서비스입니다. 운영자용 화면은 별도 저장소인 Console(`tetra/app/console`)에 있습니다.

팀 규칙에 따라 화면과 API를 **한 저장소에 두고 이미지 하나로 배포**합니다.

| 폴더 | 내용 | 상태 |
| --- | --- | --- |
| [`web/`](web/README.md) | 화면 — React 19 + TypeScript + Vite + MUI 7 | 전 화면 구현 완료 (mock 데이터, **API 미연동**) |
| `api/` | API — Spring Boot | 미착수 |

---

## 빠른 시작

```bash
git clone http://gitlab.tetra/tetra/app/portal.git
cd portal/web
npm install
npm run dev          # http://localhost:5174
```

- Node.js **20.19 이상 또는 22.12 이상**이 필요합니다(Vite 7 요구 사항, 개발 확인 버전 24.19).
- 포트 5174는 Console 화면(5173)과 함께 띄울 수 있도록 정했습니다.
- 지금은 API 없이 화면만 동작합니다. 새로고침하면 mock 데이터가 처음 상태로 돌아갑니다.

## 테스트 계정

API 연동 전까지는 아래 mock 계정으로 로그인합니다(`web/src/mocks/account.ts`).

| 구분 | 이메일 | 비밀번호 | 결과 |
| --- | --- | --- | --- |
| 테넌트 관리자 (테넌트컴퍼니, `tnt-3f8a21`) | `admin@tenant-company.com` | 아무 값 (1자 이상) | 로그인 → 이벤트 내역 |
| 로그인 실패 확인용 | 위 주소 외 아무 이메일 (예: `tenant01@gmail.com`) | 아무 값 | "로그인 실패 — 계정 정보를 찾을 수 없습니다" 배너 |

비밀번호 설정 화면은 메일 링크로 들어가는 화면이라 주소로 직접 엽니다.

| 상황 | 주소 |
| --- | --- |
| 정상 링크 | `http://localhost:5174/password/setup?token=test&email=admin@tenant-company.com` |
| 만료된 링크 | `http://localhost:5174/password/setup?token=expired` |

상태별 이벤트·알림·설정 mock 데이터는 [web/README.md의 테스트 시나리오](web/README.md#테스트-시나리오)를 보세요.

---

## 개발 규칙

전체 규칙은 팀 문서 "MVP Tetra GitLab 컨벤션"(저장소 밖에서 관리)을 따릅니다. 이 저장소에서 자주 쓰는 것만 옮겨 둡니다.

### 브랜치

- `main` 하나 + 짧게 쓰고 지우는 작업 브랜치. `main`에는 직접 push할 수 없습니다.
- 이름: `<type>/<이슈번호>-<짧은-설명>` (이슈가 없으면 번호 생략) — 예: `feat/42-event-apply-form`
- type: `feat` `fix` `refactor` `test` `docs` `chore` `ci` `hotfix`

### 커밋 메시지

```
<type>(<scope>): <요약>

<본문 — 왜 바꿨는지 (선택)>

Refs #42
```

- scope는 **`web`** 또는 **`api`** 입니다. 예: `feat(web): 이벤트 신청 폼에 엔드포인트 URL 추가`
- 요약은 한국어 가능, 50자 안팎, 마침표 없이.

### MR

- **squash 병합**이라 MR 제목이 곧 커밋 메시지입니다. 제목도 위 형식으로 씁니다.
- 작업 중이면 Draft로 올립니다.
- 병합 조건: 파이프라인 성공 + 모든 스레드 해결 + **1명 이상 승인**(CE라 강제되지 않으니 꼭 지킵니다). 병합은 Maintainer가 합니다.
- MR 템플릿 항목: 무엇을 바꿨나 / 영향 범위(dev·prod) / 어떻게 테스트했나 / 되돌리는 방법

### 하지 말 것

| ✗ | 이유 |
| --- | --- |
| 화면 빌드에 환경별 값(API 주소 등) 넣기 | prod가 dev와 **같은 이미지**를 씁니다. API는 같은 도메인의 상대 경로 `/api`로 호출합니다 |
| 비밀값을 코드·커밋·CI 로그에 남기기 | Git 이력에서 지우기 어렵습니다. 비밀값은 Secrets Manager에서 읽습니다 |
| 화면 기획 자료(와이어프레임·화면설계서) 커밋 | 저장소 밖에서 관리합니다 |

---

## 접속 (GitLab)

GitLab은 인터넷에 열려 있지 않습니다. **SSM 포트 포워딩**을 연 상태에서만 clone·pull·push가 됩니다.

1. AWS CLI와 Session Manager 플러그인을 설치합니다.
2. 관리자가 전달한 명령으로 포트 포워딩을 엽니다(hosts 설정 포함).
3. git은 HTTPS + 개인 액세스 토큰으로 인증합니다(SSH 없음). Windows는 자격 증명 관리자에 저장해 두면 됩니다.

계정은 관리자가 만들며 2단계 인증이 필수입니다.

## 빌드·배포 (예정)

`api/`가 생기면 아래 흐름으로 맞춥니다. 지금은 Dockerfile과 CI 설정이 없습니다.

- **이미지 하나**: 멀티스테이지 Dockerfile에서 `web/`을 빌드한 `dist/`를 `api/`의 `static/`에 복사합니다.
- **CI 단계**: `lint → test → build → deploy`. `lint`·`test`는 `web`·`api` job으로 나누고, 바뀐 폴더만 돌립니다(`rules: changes`).
- **배포**: `main` 병합 → ECR 이미지(태그 = 커밋 SHA) → dev ECS 갱신. Git 태그 `vX.Y.Z` → 같은 이미지를 prod로 올립니다(다시 빌드하지 않음).
- 공통 job은 `tetra/infra/infra-ci`에서 태그로 가져옵니다.

## 참고 문서 (저장소 밖)

| 문서 | 용도 |
| --- | --- |
| Tetra Portal 화면설계서 (UI-TP-01 ~ 14) | 화면 구성·문구·동작의 기준. **충돌하면 화면설계서가 우선** |
| 디자인 시안 (로그인 · 물결 무늬 · 로고) | 시각 스타일 참고 |
| 스타일 가이드 | 디자인 토큰 규칙 (Console과 공통) |
| MVP Tetra GitLab 컨벤션 · MVP GitLab CI | 브랜치·커밋·MR·CI 규칙 |
