#!/bin/bash
# 로컬 MySQL 첫 기동 때 한 번 실행된다 (docker-entrypoint-initdb.d).
# 운영(dev RDS) 스키마는 db-schema 저장소 CI(Flyway)와 Console WF5가 만든다. 이 스크립트는 로컬 개발용이다.
set -euo pipefail

export MYSQL_PWD="${MYSQL_ROOT_PASSWORD}"
run() { mysql --default-character-set=utf8mb4 -uroot "$@"; }

# V{번호}__*.sql을 번호 순서로 (Flyway와 같은 순서)
migrate() {
  local schema="$1" dir="$2"
  for f in $(ls "$dir"/V*__*.sql | sort -V); do
    run "$schema" < "$f"
  done
}

run -e "CREATE SCHEMA tetra_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
migrate tetra_platform /migration/platform

for tenant_id in ${LOCAL_TENANT_IDS}; do
  run -e "CREATE SCHEMA t_${tenant_id} DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
  migrate "t_${tenant_id}" /migration/tenant
done

# 앱 런타임 계정: tetra_platform.*와 t\_%.*에 DML만 (dev RDS의 tetra_admin_app과 같은 권한)
run -e "CREATE USER '${APP_DB_USERNAME}'@'%' IDENTIFIED BY '${APP_DB_PASSWORD}'"
run -e "GRANT SELECT, INSERT, UPDATE, DELETE ON tetra_platform.* TO '${APP_DB_USERNAME}'@'%'"
run -e "GRANT SELECT, INSERT, UPDATE, DELETE ON \`t\\_%\`.* TO '${APP_DB_USERNAME}'@'%'"

if [ -f /seed/local_seed.sql ]; then
  run tetra_platform < /seed/local_seed.sql
fi
