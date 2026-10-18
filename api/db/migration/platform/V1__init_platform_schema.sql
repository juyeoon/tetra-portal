-- =====================================================================
-- Tetra MVP 공용 스키마 (tetra_platform) 초기 마이그레이션
-- 기준 문서 : MVP 물리 ERD (2026-10-15 갱신본, layout_config 반영)
-- 대상 DB   : MySQL 8.4 / InnoDB
-- 저장 위치 : docs/db/init/tetra_platform.sql (Flyway 적용 시 db/migration/platform/V1__init_platform_schema.sql)
-- 실행 방식 : tetra_platform 스키마에서 최초 1회
-- 시드 데이터 없음 (traffic_plan, template, operator는 별도로 넣는다)
-- =====================================================================
-- 규칙
--  - 테이블 기본 문자셋 utf8mb4 / utf8mb4_0900_ai_ci
--  - 식별자·코드 컬럼(tenant_id, plan_code, Slack ID)은 ascii_bin
--  - FK는 모두 ON DELETE / ON UPDATE RESTRICT
--  - CHECK 이름은 스키마 안에서 유일해야 하므로 chk_{테이블}_{내용} 형식
--  - 일시는 KST 저장(서버·JDBC 타임존 Asia/Seoul)
-- =====================================================================

-- ---------------------------------------------------------------------
-- tenant : 테넌트 계정. 로그인 시 tenant_id를 얻는 곳
-- ---------------------------------------------------------------------
CREATE TABLE tenant (
    tenant_id                  CHAR(12)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '테넌트ID. 암호학적 난수로 생성한 소문자·숫자 12자',
    name                       VARCHAR(100) NOT NULL COMMENT '회사이름',
    root_domain                VARCHAR(253) NOT NULL COMMENT '회사도메인',
    created_at                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '가입일시',
    email                      VARCHAR(255) NOT NULL COMMENT '계정이메일(로그인 ID)',
    password                   VARCHAR(255) NULL     DEFAULT NULL COMMENT '비밀번호 해시. NULL은 미설정(로그인 항상 실패)',
    contact_name               VARCHAR(100) NOT NULL COMMENT '담당자이름',
    slack_team_id              VARCHAR(32)  CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT 'Slack팀ID. 미연동은 NULL',
    account_status             VARCHAR(20)  NOT NULL DEFAULT 'INACTIVE' COMMENT '계정상태',
    provisioning_status        VARCHAR(20)  NOT NULL DEFAULT 'PROVISIONING' COMMENT '환경준비상태. READY가 아니면 라우팅 거부',
    email_notification_enabled TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '이메일알림여부',
    CONSTRAINT pk_tenant PRIMARY KEY (tenant_id),
    CONSTRAINT uk_tenant_email UNIQUE (email),
    CONSTRAINT uk_tenant_slack_team UNIQUE (slack_team_id),
    CONSTRAINT chk_tenant_id_format CHECK (tenant_id REGEXP '^[a-z0-9]{12}$'),
    CONSTRAINT chk_tenant_account_status CHECK (account_status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT chk_tenant_provisioning_status CHECK (provisioning_status IN ('PROVISIONING','READY','FAILED')),
    INDEX idx_tenant_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='테넌트';

-- ---------------------------------------------------------------------
-- operator : 운영자 계정. DB CLI로 직접 등록
-- ---------------------------------------------------------------------
CREATE TABLE operator (
    operator_id  INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '운영자ID',
    email        VARCHAR(255) NOT NULL COMMENT '계정이메일',
    contact_name VARCHAR(100) NOT NULL COMMENT '담당자이름',
    password     VARCHAR(255) NOT NULL COMMENT '비밀번호 해시',
    CONSTRAINT pk_operator PRIMARY KEY (operator_id),
    CONSTRAINT uk_operator_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='운영자';

-- ---------------------------------------------------------------------
-- traffic_plan : 플랜 마스터 (S/M/L/XL)
-- ---------------------------------------------------------------------
CREATE TABLE traffic_plan (
    plan_code            VARCHAR(10)   CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '플랜코드 S/M/L/XL',
    max_capacity         INT UNSIGNED  NOT NULL COMMENT '최대수용인원',
    hourly_price         DECIMAL(15,2) NOT NULL COMMENT '시간당단가',
    hosting_hourly_price DECIMAL(15,2) NOT NULL COMMENT '시간당호스팅단가',
    is_visible           TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '노출여부',
    CONSTRAINT pk_traffic_plan PRIMARY KEY (plan_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='트래픽플랜';

-- ---------------------------------------------------------------------
-- template : 이벤트 타입별 페이지 앱 빌드 경로. 타입당 1행
-- 요소 위치·배경 테마는 template이 아니라 event.layout_config에 저장
-- ---------------------------------------------------------------------
CREATE TABLE template (
    template_id INT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '템플릿ID',
    event_type  VARCHAR(20)   NOT NULL COMMENT '이벤트타입',
    description VARCHAR(500)  NULL     DEFAULT NULL COMMENT '설명',
    file_path   VARCHAR(1024) NOT NULL COMMENT '템플릿 버킷의 앱 빌드 경로(예: apps/coupon/)',
    CONSTRAINT pk_template PRIMARY KEY (template_id),
    CONSTRAINT uk_template_event_type UNIQUE (event_type),
    CONSTRAINT chk_template_event_type CHECK (event_type IN ('COUPON','TIMESALE','LIVE_COMMERCE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='템플릿';

-- ---------------------------------------------------------------------
-- event_registry : 전역 event_id 발급, 라우팅, 운영 콘솔 목록용 복제 컬럼
-- 갱신은 registry → 테넌트 스키마 event 순서로 한 트랜잭션에서
-- ---------------------------------------------------------------------
CREATE TABLE event_registry (
    event_id           INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '이벤트ID. 전역 발급',
    tenant_id          CHAR(12)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '테넌트ID',
    name               VARCHAR(100) NOT NULL COMMENT '이벤트명(event 복제)',
    event_type         VARCHAR(20)  NOT NULL COMMENT '이벤트타입(event 복제)',
    traffic_plan_code  VARCHAR(10)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '플랜(event 복제)',
    hosting_start_at   DATETIME     NOT NULL COMMENT '호스팅시작일시(event 복제)',
    start_at           DATETIME     NOT NULL COMMENT '이벤트시작일시(event 복제)',
    applied_at         DATETIME     NOT NULL COMMENT '신청일시(event 복제)',
    processed_at       DATETIME     NULL     DEFAULT NULL COMMENT '처리일시(event 복제)',
    application_status VARCHAR(20)  NOT NULL DEFAULT 'APPLYING' COMMENT '신청상태(event 복제)',
    event_status       VARCHAR(20)  NULL     DEFAULT NULL COMMENT '이벤트상태(event 복제)',
    hosting_status     VARCHAR(20)  NULL     DEFAULT NULL COMMENT '호스팅상태(event 복제)',
    CONSTRAINT pk_event_registry PRIMARY KEY (event_id),
    CONSTRAINT fk_registry_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_registry_plan FOREIGN KEY (traffic_plan_code) REFERENCES traffic_plan (plan_code)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_registry_event_type CHECK (event_type IN ('COUPON','TIMESALE','LIVE_COMMERCE')),
    CONSTRAINT chk_registry_application_status CHECK (application_status IN ('APPLYING','APPLIED','APPLY_FAILED','APPROVED','REJECTED')),
    CONSTRAINT chk_registry_event_status CHECK (event_status IN ('SCHEDULED','OPEN','OPEN_FAILED','EXPIRED')),
    CONSTRAINT chk_registry_hosting_status CHECK (hosting_status IN ('PENDING','DEPLOYED','DEPLOY_FAILED','HOSTING','HOSTING_FAILED','ENDED','END_FAILED')),
    INDEX idx_registry_tenant_applied (tenant_id, applied_at),          -- OPS-06-2·06-3, FK 겸용
    INDEX idx_registry_app_status (application_status, applied_at),     -- OPS-03 목록·건수
    INDEX idx_registry_app_start (application_status, start_at),        -- OPS-04 승인됨 + 시작일 정렬
    INDEX idx_registry_hosting_status (hosting_status),                 -- OPS-04 건수
    INDEX idx_registry_event_status (event_status),                     -- OPS-02 진행 중 이벤트 수
    INDEX idx_registry_plan (traffic_plan_code)                         -- FK
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='이벤트레지스트리';

-- ---------------------------------------------------------------------
-- tenant_status_log : 테넌트 계정 상태(account_status) 변경 이력
-- ---------------------------------------------------------------------
CREATE TABLE tenant_status_log (
    log_id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '로그ID',
    tenant_id        CHAR(12)        CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '테넌트ID',
    changed_at       DATETIME        NOT NULL COMMENT '변경일시',
    prev_status      VARCHAR(20)     NULL     DEFAULT NULL COMMENT '이전상태. 최초 기록이면 NULL',
    new_status       VARCHAR(20)     NOT NULL COMMENT '변경상태',
    reason           VARCHAR(500)    NULL     DEFAULT NULL COMMENT '사유(운영자 수동 처리 시)',
    related_event_id INT UNSIGNED    NULL     DEFAULT NULL COMMENT '연관이벤트ID(자동 처리 시)',
    CONSTRAINT pk_tenant_status_log PRIMARY KEY (log_id),
    CONSTRAINT fk_tenant_status_log_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_tenant_status_log_event FOREIGN KEY (related_event_id) REFERENCES event_registry (event_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_tenant_status_log_prev CHECK (prev_status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT chk_tenant_status_log_new CHECK (new_status IN ('ACTIVE','INACTIVE')),
    INDEX idx_tenant_status_log_tenant (tenant_id, changed_at),         -- OPS-06-1, FK 겸용
    INDEX idx_tenant_status_log_event (related_event_id)                -- FK
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='테넌트상태로그';

-- ---------------------------------------------------------------------
-- operator_notification : 운영 콘솔 알림센터(OPS-08)
-- ---------------------------------------------------------------------
CREATE TABLE operator_notification (
    notification_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '알림ID',
    tenant_id       CHAR(12)        CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '테넌트ID. 테넌트 무관 알림은 NULL',
    event_id        INT UNSIGNED    NULL     DEFAULT NULL COMMENT '이벤트ID. 이벤트 무관 알림은 NULL',
    category        VARCHAR(30)     NOT NULL COMMENT '카테고리(값은 고도화 때 확정)',
    priority        VARCHAR(20)     NOT NULL COMMENT '우선순위',
    occurred_at     DATETIME        NOT NULL COMMENT '발생일시',
    content         VARCHAR(1000)   NOT NULL COMMENT '내용',
    is_read         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '읽음여부',
    CONSTRAINT pk_operator_notification PRIMARY KEY (notification_id),
    CONSTRAINT fk_operator_notif_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_operator_notif_event FOREIGN KEY (event_id) REFERENCES event_registry (event_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_operator_notif_priority CHECK (priority IN ('URGENT','WARNING','INFO')),
    INDEX idx_operator_notif_read (is_read, occurred_at),               -- OPS-08 안읽음 필터
    INDEX idx_operator_notif_occurred (occurred_at),                    -- OPS-02 최근 5건, OPS-08 전체
    INDEX idx_operator_notif_priority (priority, occurred_at),          -- OPS-08 우선순위 탭·건수
    INDEX idx_operator_notif_tenant (tenant_id),                        -- FK
    INDEX idx_operator_notif_event (event_id)                           -- FK
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='운영자알림';

-- ---------------------------------------------------------------------
-- operator_report_meta : 운영 콘솔 리포트(OPS-09)
-- ---------------------------------------------------------------------
CREATE TABLE operator_report_meta (
    report_id    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '리포트ID',
    name         VARCHAR(100)    NOT NULL COMMENT '리포트명',
    period_start DATETIME        NOT NULL COMMENT '대상기간시작',
    period_end   DATETIME        NOT NULL COMMENT '대상기간종료',
    category     VARCHAR(30)     NOT NULL COMMENT '카테고리(값은 고도화 때 확정)',
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    file_path    VARCHAR(1024)   NOT NULL COMMENT 'S3 경로',
    CONSTRAINT pk_operator_report_meta PRIMARY KEY (report_id),
    CONSTRAINT chk_operator_report_period CHECK (period_end >= period_start),
    INDEX idx_operator_report_created (created_at)                      -- OPS-09
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='운영자리포트메타';
