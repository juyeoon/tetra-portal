# db/ — 스키마 사본 (읽기 전용)

`migration/` 아래 파일은 **`tetra/app/db-schema` 저장소의 Flyway 파일을 그대로 복사한 것**이다. 시험(Testcontainers)과 로컬 MySQL(docker-compose)이 이 파일로 스키마를 만든다.

| 파일 | 원본 | 복사한 커밋 |
|---|---|---|
| `migration/platform/V1__init_platform_schema.sql` | db-schema `db/migration/platform/` | main `5211b81` (2026-10-16, 내용 같음 — 줄바꿈만 다름) |
| `migration/platform/V2__seed_plans_templates.sql` | db-schema `db/migration/platform/` | main `8bbf3e3` (2026-10-19) |
| `migration/platform/V3__hide_xl_plan.sql` | db-schema `db/migration/platform/` | main `ede1655` (2026-10-22) |
| `migration/platform/V4__add_operator_notification_dedupe_key.sql` | db-schema `db/migration/platform/` | main `1f49297` (2026-10-29) |
| `migration/tenant/V1__init_tenant_schema.sql` | db-schema `db/migration/tenant/` | main `5211b81` (2026-10-16) |

- **여기서 고치지 않는다.** 스키마 변경은 db-schema 저장소 MR(Flyway)로만 한다 — dev RDS에도 그 MR이 적용된다.
- db-schema에 새 V 파일이 병합되면 같은 이름으로 여기에 복사하고 표의 커밋을 갱신한다. 시험은 `V*`를 이름 순서로 모두 적용한다.
- 앱 계정은 DML만 있으므로 앱 코드가 스키마를 만들거나 바꾸지 않는다(`ddl-auto=none`, 시험은 `validate`).
