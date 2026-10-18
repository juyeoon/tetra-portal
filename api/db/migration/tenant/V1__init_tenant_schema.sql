-- =====================================================================
-- Tetra MVP 테넌트 스키마 (t_{tenant_id}) 초기 마이그레이션
-- 기준 문서 : MVP 물리 ERD (2026-10-15 갱신본, layout_config 반영)
-- 대상 DB   : MySQL 8.4 / InnoDB
-- 저장 위치 : docs/db/init/tenant_schema.sql (Flyway 적용 시 db/migration/tenant/V1__init_tenant_schema.sql)
-- 실행 방식 : 테넌트 등록(WF5) 때 마이그레이션 계정으로
--             CREATE SCHEMA t_{tenant_id} DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
--             후 해당 스키마에서 실행
-- 전제     : 공용 스키마 tetra_platform(tetra_platform.sql)이 먼저 있어야 함
-- 시드 데이터 없음
-- =====================================================================
-- 규칙
--  - 테이블 이름에 스키마를 붙이지 않는다(실행 스키마에 생성). 공용 참조만 tetra_platform. 으로 지정
--  - tenant_id는 FK 없이 형식 CHECK만 둔다(issuance_history는 CHECK도 없음, 핫패스)
--    단, tenant_slack_config는 tenant_id가 PK라 FK를 건다
--  - FK는 모두 ON DELETE / ON UPDATE RESTRICT
--  - CHECK 이름은 스키마 안에서 유일해야 하므로 chk_{테이블}_{내용} 형식
-- =====================================================================

-- ---------------------------------------------------------------------
-- event : 이벤트 본체. event_id는 tetra_platform.event_registry가 발급한 값
-- ---------------------------------------------------------------------
CREATE TABLE event (
    event_id           INT UNSIGNED  NOT NULL COMMENT '이벤트ID. registry 발급값(AUTO_INCREMENT 없음)',
    tenant_id          CHAR(12)      CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '테넌트ID',
    name               VARCHAR(100)  NOT NULL COMMENT '이벤트명',
    event_type         VARCHAR(20)   NOT NULL COMMENT '이벤트타입. 1차는 COUPON',
    template_id        INT UNSIGNED  NOT NULL COMMENT '템플릿ID(이벤트 타입의 앱 빌드)',
    layout_config      JSON          NOT NULL COMMENT '화면설정. 요소 4개의 가로(h)·세로(v) 위치 + 배경 테마. event.json layout으로 그대로 전달',
    banner_image_path  VARCHAR(1024) NULL     DEFAULT NULL COMMENT 'assets/banners/{event_id}/. 신청 중(APPLYING)에는 NULL',
    start_at           DATETIME      NOT NULL COMMENT '이벤트시작일시',
    end_at             DATETIME      NOT NULL COMMENT '이벤트종료일시',
    hosting_start_at   DATETIME      NOT NULL COMMENT '호스팅시작일시',
    hosting_end_at     DATETIME      NOT NULL COMMENT '호스팅종료일시',
    endpoint_url       VARCHAR(2048) NOT NULL COMMENT '엔드포인트URL',
    traffic_plan_code  VARCHAR(10)   CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '플랜',
    stock_count        INT UNSIGNED  NOT NULL COMMENT '발급가능수량',
    slug               CHAR(8)       CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '슬러그. 승인 전 NULL, CSPRNG 소문자·숫자 8자',
    applied_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '신청일시',
    processed_by       INT UNSIGNED  NULL     DEFAULT NULL COMMENT '처리운영자ID',
    processed_at       DATETIME      NULL     DEFAULT NULL COMMENT '처리일시',
    reject_reason      VARCHAR(500)  NULL     DEFAULT NULL COMMENT '반려사유',
    application_status VARCHAR(20)   NOT NULL DEFAULT 'APPLYING' COMMENT '신청상태',
    hosting_status     VARCHAR(20)   NULL     DEFAULT NULL COMMENT '호스팅상태. 승인 전 NULL',
    event_status       VARCHAR(20)   NULL     DEFAULT NULL COMMENT '이벤트상태. 승인 전 NULL',
    CONSTRAINT pk_event PRIMARY KEY (event_id),
    CONSTRAINT uk_event_slug UNIQUE (slug),
    CONSTRAINT fk_event_registry FOREIGN KEY (event_id) REFERENCES tetra_platform.event_registry (event_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_event_template FOREIGN KEY (template_id) REFERENCES tetra_platform.template (template_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_event_plan FOREIGN KEY (traffic_plan_code) REFERENCES tetra_platform.traffic_plan (plan_code)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_event_operator FOREIGN KEY (processed_by) REFERENCES tetra_platform.operator (operator_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_event_tenant_id CHECK (tenant_id REGEXP '^[a-z0-9]{12}$'),
    CONSTRAINT chk_event_event_type CHECK (event_type IN ('COUPON','TIMESALE','LIVE_COMMERCE')),
    CONSTRAINT chk_event_period CHECK (end_at > start_at),
    CONSTRAINT chk_event_hosting_period CHECK (hosting_end_at > hosting_start_at),
    CONSTRAINT chk_event_stock CHECK (stock_count > 0),
    CONSTRAINT chk_event_slug_format CHECK (slug REGEXP '^[a-z0-9]{8}$'),
    CONSTRAINT chk_event_application_status CHECK (application_status IN ('APPLYING','APPLIED','APPLY_FAILED','APPROVED','REJECTED')),
    CONSTRAINT chk_event_hosting_status CHECK (hosting_status IN ('PENDING','DEPLOYED','DEPLOY_FAILED','HOSTING','HOSTING_FAILED','ENDED','END_FAILED')),
    CONSTRAINT chk_event_event_status CHECK (event_status IN ('SCHEDULED','OPEN','OPEN_FAILED','EXPIRED')),
    -- 화면설정 형식: UI-TP-09 위젯 값
    CONSTRAINT chk_event_layout_config CHECK (JSON_SCHEMA_VALID('{
        "type": "object",
        "additionalProperties": false,
        "required": ["title", "banner", "couponList", "issueButton", "theme"],
        "definitions": {
            "pos": {
                "type": "object",
                "additionalProperties": false,
                "required": ["h", "v"],
                "properties": {
                    "h": {"enum": ["LEFT", "CENTER", "RIGHT"]},
                    "v": {"enum": ["TOP", "MIDDLE", "BOTTOM"]}
                }
            }
        },
        "properties": {
            "title":       {"$ref": "#/definitions/pos"},
            "banner":      {"$ref": "#/definitions/pos"},
            "couponList":  {"$ref": "#/definitions/pos"},
            "issueButton": {"$ref": "#/definitions/pos"},
            "theme":       {"enum": ["WHITE", "WARM_ORANGE", "DARK", "BLUE"]}
        }
    }', layout_config)),
    -- 승인된 건만 호스팅·이벤트 상태를 가진다
    CONSTRAINT chk_event_approved CHECK ((application_status = 'APPROVED') = (hosting_status IS NOT NULL AND event_status IS NOT NULL)),
    -- 반려된 건만 반려 사유를 가진다
    CONSTRAINT chk_event_reject CHECK ((application_status = 'REJECTED') = (reject_reason IS NOT NULL)),
    -- 호스팅 기간이 이벤트 기간을 포함
    CONSTRAINT chk_event_hosting_cover CHECK (hosting_start_at <= start_at AND hosting_end_at >= end_at),
    -- 신청 성공 이후에는 배너 필수
    CONSTRAINT chk_event_banner CHECK (application_status IN ('APPLYING','APPLY_FAILED') OR banner_image_path IS NOT NULL),
    INDEX idx_event_applied (applied_at),                               -- UI-TP-05 신청일 정렬
    INDEX idx_event_start (start_at),                                   -- UI-TP-05 이벤트기간 정렬
    INDEX idx_event_template (template_id),                             -- FK
    INDEX idx_event_plan (traffic_plan_code),                           -- FK
    INDEX idx_event_operator (processed_by)                             -- FK
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='이벤트';

-- ---------------------------------------------------------------------
-- tenant_slack_config : 테넌트 Slack 연동 정보. 스키마당 최대 1행
-- ---------------------------------------------------------------------
CREATE TABLE tenant_slack_config (
    tenant_id            CHAR(12)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '테넌트ID',
    workspace_name       VARCHAR(100) NOT NULL COMMENT '워크스페이스명',
    bot_token            VARCHAR(512) NOT NULL COMMENT '봇토큰(앱에서 암호화한 값)',
    bot_user_id          VARCHAR(32)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '봇유저ID',
    authed_user_id       VARCHAR(32)  CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '연동진행자ID',
    authed_user_email    VARCHAR(255) NULL     DEFAULT NULL COMMENT '연동진행자이메일',
    channel_urgent_id    VARCHAR(32)  CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '긴급채널ID',
    channel_urgent_name  VARCHAR(80)  NULL     DEFAULT NULL COMMENT '긴급채널명',
    channel_warning_id   VARCHAR(32)  CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '경고채널ID',
    channel_warning_name VARCHAR(80)  NULL     DEFAULT NULL COMMENT '경고채널명',
    channel_info_id      VARCHAR(32)  CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '정보채널ID',
    channel_info_name    VARCHAR(80)  NULL     DEFAULT NULL COMMENT '정보채널명',
    connection_status    VARCHAR(20)  NOT NULL DEFAULT 'CONNECTED' COMMENT '연동상태',
    connected_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '연동일시',
    CONSTRAINT pk_tenant_slack_config PRIMARY KEY (tenant_id),
    CONSTRAINT fk_slack_config_tenant FOREIGN KEY (tenant_id) REFERENCES tetra_platform.tenant (tenant_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_slack_config_tenant_id CHECK (tenant_id REGEXP '^[a-z0-9]{12}$'),
    CONSTRAINT chk_slack_config_connection_status CHECK (connection_status IN ('CONNECTED','REVOKED','ERROR'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='테넌트Slack설정';

-- ---------------------------------------------------------------------
-- tenant_notification : Tetra Portal 알림센터(UI-TP-13)
-- ---------------------------------------------------------------------
CREATE TABLE tenant_notification (
    notification_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '알림ID',
    tenant_id       CHAR(12)        CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '테넌트ID',
    event_id        INT UNSIGNED    NULL     DEFAULT NULL COMMENT '이벤트ID. 이벤트 무관 알림은 NULL',
    category        VARCHAR(30)     NOT NULL COMMENT '카테고리(값은 고도화 때 확정)',
    priority        VARCHAR(20)     NOT NULL COMMENT '우선순위',
    occurred_at     DATETIME        NOT NULL COMMENT '발생일시',
    content         VARCHAR(1000)   NOT NULL COMMENT '내용',
    is_read         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '읽음여부',
    CONSTRAINT pk_tenant_notification PRIMARY KEY (notification_id),
    CONSTRAINT fk_tenant_notif_event FOREIGN KEY (event_id) REFERENCES event (event_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_tenant_notif_tenant_id CHECK (tenant_id REGEXP '^[a-z0-9]{12}$'),
    CONSTRAINT chk_tenant_notif_priority CHECK (priority IN ('URGENT','WARNING','INFO')),
    INDEX idx_tenant_notif_read (is_read, occurred_at),                 -- UI-TP-13 안읽음 탭
    INDEX idx_tenant_notif_occurred (occurred_at),                      -- UI-TP-13 전체보기 최신순
    INDEX idx_tenant_notif_priority (priority, occurred_at),            -- UI-TP-13 우선순위 탭·건수
    INDEX idx_tenant_notif_event (event_id)                             -- FK
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='테넌트알림';

-- ---------------------------------------------------------------------
-- tenant_report_meta : Tetra Portal 리포트(UI-TP-12)
-- ---------------------------------------------------------------------
CREATE TABLE tenant_report_meta (
    report_id    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '리포트ID',
    tenant_id    CHAR(12)        CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '테넌트ID',
    name         VARCHAR(100)    NOT NULL COMMENT '리포트명',
    period_start DATETIME        NOT NULL COMMENT '대상기간시작',
    period_end   DATETIME        NOT NULL COMMENT '대상기간종료',
    category     VARCHAR(30)     NOT NULL COMMENT '카테고리(값은 고도화 때 확정)',
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    file_path    VARCHAR(1024)   NOT NULL COMMENT '테넌트 로그 버킷 경로',
    CONSTRAINT pk_tenant_report_meta PRIMARY KEY (report_id),
    CONSTRAINT chk_tenant_report_tenant_id CHECK (tenant_id REGEXP '^[a-z0-9]{12}$'),
    CONSTRAINT chk_tenant_report_period CHECK (period_end >= period_start),
    INDEX idx_tenant_report_created (created_at)                        -- UI-TP-12
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='테넌트리포트메타';

-- ---------------------------------------------------------------------
-- event_auth_key : 발급 API JWT 검증용 공개키. 개인키는 저장하지 않음
-- ---------------------------------------------------------------------
CREATE TABLE event_auth_key (
    key_id      INT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '키ID',
    event_id    INT UNSIGNED  NOT NULL COMMENT '이벤트ID',
    tenant_id   CHAR(12)      CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '테넌트ID',
    public_key  VARCHAR(2048) NOT NULL COMMENT 'RS256 공개키(PEM)',
    key_version INT UNSIGNED  NOT NULL DEFAULT 1 COMMENT '키버전(JWT kid). 재발급 시 +1',
    status      VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE' COMMENT '상태',
    issued_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '발급일시',
    expires_at  DATETIME      NOT NULL COMMENT '만료예정일시. 발급 시 hosting_end_at',
    revoked_at  DATETIME      NULL     DEFAULT NULL COMMENT '비활성일시',
    CONSTRAINT pk_event_auth_key PRIMARY KEY (key_id),
    CONSTRAINT uk_auth_key_event_version UNIQUE (event_id, key_version),  -- kid 조회, FK 겸용
    CONSTRAINT fk_auth_key_event FOREIGN KEY (event_id) REFERENCES event (event_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_auth_key_tenant_id CHECK (tenant_id REGEXP '^[a-z0-9]{12}$'),
    CONSTRAINT chk_auth_key_status CHECK (status IN ('ACTIVE','EXPIRED','REVOKED')),
    CONSTRAINT chk_auth_key_expires CHECK (expires_at > issued_at),
    CONSTRAINT chk_auth_key_revoked CHECK ((status = 'ACTIVE' AND revoked_at IS NULL) OR (status IN ('EXPIRED','REVOKED') AND revoked_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='이벤트인증키';

-- ---------------------------------------------------------------------
-- coupon : 이벤트별 쿠폰(최대 10종, 앱 검증). 재고는 event.stock_count
-- ---------------------------------------------------------------------
CREATE TABLE coupon (
    coupon_id   INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '쿠폰ID(노출 순서)',
    event_id    INT UNSIGNED NOT NULL COMMENT '이벤트ID',
    tenant_id   CHAR(12)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '테넌트ID',
    name        VARCHAR(100) NOT NULL COMMENT '쿠폰명',
    description VARCHAR(500) NOT NULL COMMENT '화면 노출 문구',
    CONSTRAINT pk_coupon PRIMARY KEY (coupon_id),
    CONSTRAINT fk_coupon_event FOREIGN KEY (event_id) REFERENCES event (event_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_coupon_tenant_id CHECK (tenant_id REGEXP '^[a-z0-9]{12}$'),
    INDEX idx_coupon_event (event_id)                                   -- FK
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='쿠폰';

-- ---------------------------------------------------------------------
-- issuance_history : 발급 결과 확정 시 1회 INSERT (핫패스)
-- 보조 인덱스는 UNIQUE 하나만. 중복 키 오류(1062)는 앱에서 "이미 기록됨"으로 처리
-- ---------------------------------------------------------------------
CREATE TABLE issuance_history (
    issuance_id       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '발급이력ID',
    event_id          INT UNSIGNED    NOT NULL COMMENT '이벤트ID',
    tenant_id         CHAR(12)        CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '테넌트ID(핫패스라 CHECK 없음)',
    user_id           VARCHAR(128)    CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '사용자ID(대소문자 구분)',
    ticket_number     INT UNSIGNED    NOT NULL COMMENT '번호표',
    session_issued_at DATETIME(3)     NOT NULL COMMENT '세션발급일시',
    queue_entered_at  DATETIME(3)     NOT NULL COMMENT '대기열진입일시',
    issued_at         DATETIME(3)     NOT NULL COMMENT '결과확정일시',
    result            VARCHAR(20)     NOT NULL COMMENT '발급결과. 기본값 없이 항상 명시',
    CONSTRAINT pk_issuance_history PRIMARY KEY (issuance_id),
    CONSTRAINT uk_issuance_event_user UNIQUE (event_id, user_id),       -- 중복 방지·집계·CSV, FK 겸용
    CONSTRAINT fk_issuance_event FOREIGN KEY (event_id) REFERENCES event (event_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_issuance_result CHECK (result IN ('SUCCESS','FAILED_SOLDOUT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='발급이력';

-- ---------------------------------------------------------------------
-- event_billing : 이벤트 비용(1:1). 결제 기능 없음
-- ---------------------------------------------------------------------
CREATE TABLE event_billing (
    event_id                     INT UNSIGNED  NOT NULL COMMENT '이벤트ID',
    tenant_id                    CHAR(12)      CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '테넌트ID',
    applied_hourly_price         DECIMAL(15,2) NOT NULL COMMENT '적용시간당단가(신청 시점)',
    applied_hosting_hourly_price DECIMAL(15,2) NOT NULL COMMENT '적용호스팅단가(신청 시점)',
    estimated_cost               DECIMAL(15,2) NOT NULL COMMENT '예상비용',
    actual_cost                  DECIMAL(15,2) NULL DEFAULT NULL COMMENT '실제비용(1차 미사용)',
    budget_alert_threshold       DECIMAL(15,2) NULL DEFAULT NULL COMMENT '예산임계값(1차 미사용)',
    extracharge_amount           DECIMAL(15,2) NULL DEFAULT NULL COMMENT '추가요금(1차 미사용)',
    CONSTRAINT pk_event_billing PRIMARY KEY (event_id),
    CONSTRAINT fk_billing_event FOREIGN KEY (event_id) REFERENCES event (event_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_billing_tenant_id CHECK (tenant_id REGEXP '^[a-z0-9]{12}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='이벤트비용';

-- ---------------------------------------------------------------------
-- event_result_summary : 이벤트 결과 요약(1:1)
-- 집계 Job이 INSERT(Redis 지표), 발급 이력 저장 API가 UPDATE(DB 지표·CSV)
-- 두 단계 모두 ON DUPLICATE KEY UPDATE로 재시도에 안전하게 씀
-- ---------------------------------------------------------------------
CREATE TABLE event_result_summary (
    event_id             INT UNSIGNED  NOT NULL COMMENT '이벤트ID',
    tenant_id            CHAR(12)      CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '테넌트ID',
    session_count        INT UNSIGNED  NOT NULL COMMENT '세션수(집계 Job)',
    ticket_count         INT UNSIGNED  NOT NULL COMMENT '번호표수(집계 Job)',
    peak_concurrent      INT UNSIGNED  NOT NULL COMMENT '최대동시접속자수(집계 Job)',
    peak_at              DATETIME      NULL DEFAULT NULL COMMENT '최대동시접속시각. 접속자가 없으면 NULL',
    metrics_finalized_at DATETIME      NOT NULL COMMENT '집계확정일시(집계 Job)',
    total_stock          INT UNSIGNED  NULL DEFAULT NULL COMMENT '총재고(발급 이력 저장 API)',
    success_count        INT UNSIGNED  NULL DEFAULT NULL COMMENT '발급성공수(발급 이력 저장 API)',
    soldout_count        INT UNSIGNED  NULL DEFAULT NULL COMMENT '품절실패수(발급 이력 저장 API)',
    sellout_rate         DECIMAL(5,4)  NULL DEFAULT NULL COMMENT '소진율 0~1(발급 이력 저장 API)',
    history_file_path    VARCHAR(1024) NULL DEFAULT NULL COMMENT '발급이력 CSV 경로(발급 이력 저장 API)',
    history_generated_at DATETIME      NULL DEFAULT NULL COMMENT '발급이력생성일시(발급 이력 저장 API)',
    CONSTRAINT pk_event_result_summary PRIMARY KEY (event_id),
    CONSTRAINT fk_summary_event FOREIGN KEY (event_id) REFERENCES event (event_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_summary_tenant_id CHECK (tenant_id REGEXP '^[a-z0-9]{12}$'),
    CONSTRAINT chk_summary_sellout_rate CHECK (sellout_rate BETWEEN 0 AND 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='이벤트결과요약';

-- ---------------------------------------------------------------------
-- event_status_log : 신청·호스팅·이벤트 상태 변경 이력(통합)
-- 상태를 바꾸는 서비스 메서드가 event·registry 갱신과 같은 트랜잭션에서 INSERT
-- ---------------------------------------------------------------------
CREATE TABLE event_status_log (
    log_id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '로그ID(같은 시각 기록의 순서)',
    event_id    INT UNSIGNED    NOT NULL COMMENT '이벤트ID',
    tenant_id   CHAR(12)        CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '테넌트ID',
    status_type VARCHAR(20)     NOT NULL COMMENT '상태구분',
    prev_status VARCHAR(20)     NULL     DEFAULT NULL COMMENT '이전상태. 최초 기록이면 NULL',
    new_status  VARCHAR(20)     NOT NULL COMMENT '변경상태',
    changed_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '변경일시',
    detail      VARCHAR(500)    NULL     DEFAULT NULL COMMENT '상세(실패 원인 등)',
    changed_by  INT UNSIGNED    NULL     DEFAULT NULL COMMENT '처리운영자ID. 시스템 처리면 NULL',
    CONSTRAINT pk_event_status_log PRIMARY KEY (log_id),
    CONSTRAINT fk_status_log_event FOREIGN KEY (event_id) REFERENCES event (event_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_status_log_operator FOREIGN KEY (changed_by) REFERENCES tetra_platform.operator (operator_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_status_log_tenant_id CHECK (tenant_id REGEXP '^[a-z0-9]{12}$'),
    CONSTRAINT chk_status_log_type CHECK (status_type IN ('APPLICATION','HOSTING','EVENT')),
    -- status_type별로 prev/new 값이 event의 해당 상태 컬럼 값 목록 안에 있어야 함
    CONSTRAINT chk_status_log_value CHECK (
        (status_type = 'APPLICATION'
            AND new_status IN ('APPLYING','APPLIED','APPLY_FAILED','APPROVED','REJECTED')
            AND (prev_status IS NULL OR prev_status IN ('APPLYING','APPLIED','APPLY_FAILED','APPROVED','REJECTED')))
     OR (status_type = 'HOSTING'
            AND new_status IN ('PENDING','DEPLOYED','DEPLOY_FAILED','HOSTING','HOSTING_FAILED','ENDED','END_FAILED')
            AND (prev_status IS NULL OR prev_status IN ('PENDING','DEPLOYED','DEPLOY_FAILED','HOSTING','HOSTING_FAILED','ENDED','END_FAILED')))
     OR (status_type = 'EVENT'
            AND new_status IN ('SCHEDULED','OPEN','OPEN_FAILED','EXPIRED')
            AND (prev_status IS NULL OR prev_status IN ('SCHEDULED','OPEN','OPEN_FAILED','EXPIRED')))
    ),
    INDEX idx_status_log_event (event_id, changed_at),                  -- UI-TP-06, OPS-03-1, FK 겸용
    INDEX idx_status_log_operator (changed_by)                          -- FK
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='이벤트상태로그';
