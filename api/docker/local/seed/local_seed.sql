-- 로컬 개발 전용 seed (tetra_platform). 운영 값이 아니다.
-- 요금제 · 템플릿 실제 값은 db-schema 시드 V2(계획서 Q3 · Q12)가 정해지면 그 값으로 바꾼다. 지금은 Console 테스트 seed와 같은 값.
INSERT INTO traffic_plan (plan_code, max_capacity, hourly_price, hosting_hourly_price, is_visible) VALUES
    ('S', 10000, 10000.00, 1000.00, 1),
    ('M', 30000, 25000.00, 2000.00, 1),
    ('L', 50000, 40000.00, 3000.00, 1),
    ('XL', 100000, 70000.00, 5000.00, 0);

INSERT INTO template (template_id, event_type, description, file_path) VALUES
    (1, 'COUPON', '쿠폰 이벤트 앱', 'apps/coupon/');

-- 테넌트 2개 (스키마는 00-init.sh의 LOCAL_TENANT_IDS). 비밀번호는 아직 없다(= Console WF5 등록 직후) — P1에서 설정 흐름으로 넣는다
INSERT INTO tenant (tenant_id, name, root_domain, created_at, email, password, contact_name, account_status, provisioning_status) VALUES
    ('k7f2m9qx4b1t', '테넌트컴퍼니', 'tetraops.cloud', '2026-10-01 10:00:00', 'admin@tenant-company.com', NULL, '다란', 'INACTIVE', 'READY'),
    ('d4n8p2r6s0v1', '두번째상점', 'tetraops.cloud', '2026-10-02 10:00:00', 'owner@second-shop.com', NULL, '김담당', 'ACTIVE', 'READY');
