-- =====================================================================
-- 운영자 알림 중복 방지 키 dedupe_key (2026-10-29)
--  - DLQ 메시지 · 단계 지연은 상태 전이가 아니라 1분 주기 조회로 감지되므로, 같은 키는 1행만 남긴다.
--    키 예) DLQ:{큐}:{yyyy-MM-dd}
--           DELAY:{event_id}:{단계 코드 HOSTING_START · PREPARE_START · ENTRY_OPEN · END_START · END_DONE}
--  - 기존 행과 상태 전이 알림은 NULL 로 둔다(UNIQUE 여도 NULL 은 여러 행 허용).
--  - 온라인 DDL(ALGORITHM=INPLACE, LOCK=NONE)이라 알림 쓰기를 막지 않는다.
--    열 추가와 UNIQUE 를 한 문장으로 묶어 원자적이다(실패하면 아무것도 바뀌지 않는다).
-- 재실행 안전 아님: MySQL 에는 ADD COLUMN IF NOT EXISTS 가 없다. 실패하면 flyway repair 후 다시 migrate
-- =====================================================================

-- 메타데이터 잠금(MDL)을 10초까지만 기다리고 실패한다. 기본값(1년)이면 긴 트랜잭션 뒤에서
-- ALTER 가 기다리는 동안 이 테이블을 쓰는 다른 쿼리까지 그 뒤에 줄줄이 막힌다.
SET SESSION lock_wait_timeout = 10;

ALTER TABLE operator_notification
    ADD COLUMN dedupe_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL
        COMMENT '중복방지키. 같은 키는 1행만(DLQ:{큐}:{yyyy-MM-dd} · DELAY:{event_id}:{단계}). NULL은 제한 없음',
    ADD UNIQUE KEY uk_operator_notif_dedupe (dedupe_key),
    ALGORITHM=INPLACE, LOCK=NONE;
