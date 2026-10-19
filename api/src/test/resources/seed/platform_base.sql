-- 시험 공통 seed (tetra_platform). V1 DDL에는 seed가 없으므로 시험이 직접 넣는다. TestData.reset()은 지우지 않는다.
-- 요금제 S · M · L · XL · 템플릿은 db/migration/platform/V2(db-schema 시드)가 넣는다.
-- 여기서는 노출 규칙 시험용 숨김 요금제 HIDDEN 하나만 더 둔다.
INSERT INTO traffic_plan (plan_code, max_capacity, hourly_price, hosting_hourly_price, is_visible) VALUES
    ('HIDDEN', 1000, 1000.00, 100.00, 0);

-- 승인 · 반려된 이벤트의 processed_by(fk_event_operator)용 운영자 1명. Portal은 operator를 읽지 않는다
INSERT INTO operator (operator_id, email, contact_name, password)
VALUES (1, 'operator@tetra-operation.com', '홍길동', '$2a$10$okElfhEuxbaHWkowSCIryez9gAIoLsJ78Jxo9f/j1vYHXyif9Jlhu');
