-- 시험 공통 seed (tetra_platform). V1 DDL에는 seed가 없으므로 시험이 직접 넣는다. TestData.reset()은 지우지 않는다.
-- 요금제 값은 Console 시험 seed와 같다(실제 값은 db-schema 시드 V2 — 계획서 Q3 · Q12). XL은 숨김.
INSERT INTO traffic_plan (plan_code, max_capacity, hourly_price, hosting_hourly_price, is_visible) VALUES
    ('S', 10000, 10000.00, 1000.00, 1),
    ('M', 30000, 25000.00, 2000.00, 1),
    ('L', 50000, 40000.00, 3000.00, 1),
    ('XL', 100000, 70000.00, 5000.00, 0);

-- 승인 · 반려된 이벤트의 processed_by(fk_event_operator)용 운영자 1명. Portal은 operator를 읽지 않는다
INSERT INTO operator (operator_id, email, contact_name, password)
VALUES (1, 'operator@tetra-operation.com', '홍길동', '$2a$10$okElfhEuxbaHWkowSCIryez9gAIoLsJ78Jxo9f/j1vYHXyif9Jlhu');

INSERT INTO template (template_id, event_type, description, file_path)
VALUES (1, 'COUPON', '쿠폰 이벤트 앱', 'apps/coupon/');
