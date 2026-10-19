-- 로컬 개발 전용 seed (tetra_platform). 운영 값이 아니다.
-- 승인 · 반려 이벤트의 processed_by(fk_event_operator)용 운영자. Portal은 읽지 않는다
INSERT INTO operator (operator_id, email, contact_name, password) VALUES
    (1, 'operator@tetra-operation.com', '홍길동', '$2a$10$okElfhEuxbaHWkowSCIryez9gAIoLsJ78Jxo9f/j1vYHXyif9Jlhu');

-- 요금제 · 템플릿 실제 값은 db-schema 시드 V2(계획서 Q3 · Q12)가 정해지면 그 값으로 바꾼다. 지금은 Console 테스트 seed와 같은 값.
INSERT INTO traffic_plan (plan_code, max_capacity, hourly_price, hosting_hourly_price, is_visible) VALUES
    ('S', 10000, 10000.00, 1000.00, 1),
    ('M', 30000, 25000.00, 2000.00, 1),
    ('L', 50000, 40000.00, 3000.00, 1),
    ('XL', 100000, 70000.00, 5000.00, 0);

INSERT INTO template (template_id, event_type, description, file_path) VALUES
    (1, 'COUPON', '쿠폰 이벤트 앱', 'apps/coupon/');

-- 테넌트 2개 (스키마는 00-init.sh의 LOCAL_TENANT_IDS). 비밀번호는 아직 없다(= Console WF5 등록 직후) — docker/local/password-link.ps1로 설정
INSERT INTO tenant (tenant_id, name, root_domain, created_at, email, password, contact_name, account_status, provisioning_status) VALUES
    ('k7f2m9qx4b1t', '테넌트컴퍼니', 'tetraops.cloud', '2026-10-01 10:00:00', 'admin@tenant-company.com', NULL, '다란', 'INACTIVE', 'READY'),
    ('d4n8p2r6s0v1', '두번째상점', 'tetraops.cloud', '2026-10-02 10:00:00', 'owner@second-shop.com', NULL, '김담당', 'ACTIVE', 'READY');

-- ---------------------------------------------------------------- 테넌트컴퍼니(k7f2m9qx4b1t)의 상태별 이벤트 (화면 확인용)
-- event_id는 event_registry가 발급하는 값 — 같은 번호로 registry와 테넌트 스키마에 같은 상태로 넣는다
SET @L = '{"title":{"h":"CENTER","v":"TOP"},"banner":{"h":"CENTER","v":"TOP"},"couponList":{"h":"CENTER","v":"MIDDLE"},"issueButton":{"h":"CENTER","v":"BOTTOM"},"theme":"WHITE"}';

INSERT INTO event_registry (event_id, tenant_id, name, event_type, traffic_plan_code, hosting_start_at, start_at, applied_at, processed_at, application_status, event_status, hosting_status) VALUES
    (1001, 'k7f2m9qx4b1t', '블랙프라이데이 쿠폰', 'COUPON', 'M', '2026-11-27 18:00:00', '2026-11-27 20:00:00', '2026-10-10 09:00:00', NULL, 'APPLIED', NULL, NULL),
    (1002, 'k7f2m9qx4b1t', '가을 정기 세일 쿠폰', 'COUPON', 'L', '2026-10-20 08:00:00', '2026-10-20 10:00:00', '2026-10-05 14:00:00', '2026-10-06 10:00:00', 'APPROVED', 'SCHEDULED', 'PENDING'),
    (1003, 'k7f2m9qx4b1t', '추석 감사 쿠폰', 'COUPON', 'S', '2026-09-25 08:00:00', '2026-09-25 10:00:00', '2026-09-10 11:00:00', '2026-09-11 09:00:00', 'APPROVED', 'EXPIRED', 'ENDED'),
    (1004, 'k7f2m9qx4b1t', '연말 감사 쿠폰', 'COUPON', 'M', '2026-12-24 08:00:00', '2026-12-24 10:00:00', '2026-10-12 16:00:00', '2026-10-13 10:00:00', 'REJECTED', NULL, NULL),
    (1005, 'k7f2m9qx4b1t', '신규 가입 쿠폰', 'COUPON', 'S', '2026-11-10 08:00:00', '2026-11-10 10:00:00', '2026-10-15 13:00:00', NULL, 'APPLY_FAILED', NULL, NULL);

INSERT INTO t_k7f2m9qx4b1t.event (event_id, tenant_id, name, event_type, template_id, layout_config, banner_image_path, start_at, end_at, hosting_start_at, hosting_end_at, endpoint_url, traffic_plan_code, stock_count, slug, applied_at, processed_by, processed_at, reject_reason, application_status, hosting_status, event_status) VALUES
    (1001, 'k7f2m9qx4b1t', '블랙프라이데이 쿠폰', 'COUPON', 1, @L, 'assets/banners/1001/banner.png', '2026-11-27 20:00:00', '2026-11-27 23:00:00', '2026-11-27 18:00:00', '2026-11-28 01:00:00', 'https://shop.tenant-company.com/event/bf', 'M', 30000, NULL, '2026-10-10 09:00:00', NULL, NULL, NULL, 'APPLIED', NULL, NULL),
    (1002, 'k7f2m9qx4b1t', '가을 정기 세일 쿠폰', 'COUPON', 1, @L, 'assets/banners/1002/banner.png', '2026-10-20 10:00:00', '2026-10-20 12:00:00', '2026-10-20 08:00:00', '2026-10-20 14:00:00', 'https://shop.tenant-company.com/event/fall', 'L', 50000, 'fall2026', '2026-10-05 14:00:00', 1, '2026-10-06 10:00:00', NULL, 'APPROVED', 'PENDING', 'SCHEDULED'),
    (1003, 'k7f2m9qx4b1t', '추석 감사 쿠폰', 'COUPON', 1, @L, 'assets/banners/1003/banner.png', '2026-09-25 10:00:00', '2026-09-25 11:00:00', '2026-09-25 08:00:00', '2026-09-25 13:00:00', 'https://shop.tenant-company.com/event/chuseok', 'S', 10000, 'chsk2609', '2026-09-10 11:00:00', 1, '2026-09-11 09:00:00', NULL, 'APPROVED', 'ENDED', 'EXPIRED'),
    (1004, 'k7f2m9qx4b1t', '연말 감사 쿠폰', 'COUPON', 1, @L, 'assets/banners/1004/banner.png', '2026-12-24 10:00:00', '2026-12-24 12:00:00', '2026-12-24 08:00:00', '2026-12-24 14:00:00', 'https://shop.tenant-company.com/event/xmas', 'M', 20000, NULL, '2026-10-12 16:00:00', 1, '2026-10-13 10:00:00', '이벤트 기간에 정기 점검이 있어 날짜를 옮겨 다시 신청해 주세요.', 'REJECTED', NULL, NULL),
    (1005, 'k7f2m9qx4b1t', '신규 가입 쿠폰', 'COUPON', 1, @L, NULL, '2026-11-10 10:00:00', '2026-11-10 12:00:00', '2026-11-10 08:00:00', '2026-11-10 14:00:00', 'https://shop.tenant-company.com/event/welcome', 'S', 5000, NULL, '2026-10-15 13:00:00', NULL, NULL, NULL, 'APPLY_FAILED', NULL, NULL);

INSERT INTO t_k7f2m9qx4b1t.coupon (event_id, tenant_id, name, description) VALUES
    (1001, 'k7f2m9qx4b1t', '20% 할인 쿠폰', '전 상품 20% 할인'),
    (1001, 'k7f2m9qx4b1t', '무료 배송 쿠폰', '3만원 이상 무료 배송'),
    (1002, 'k7f2m9qx4b1t', '가을 10% 쿠폰', '가을 신상품 10% 할인'),
    (1003, 'k7f2m9qx4b1t', '추석 5천원 쿠폰', '5만원 이상 5천원 할인'),
    (1004, 'k7f2m9qx4b1t', '연말 15% 쿠폰', '전 상품 15% 할인'),
    (1005, 'k7f2m9qx4b1t', '가입 축하 쿠폰', '첫 구매 3천원 할인');

-- 예상 비용 = ceil(호스팅 시간) × (요금제 단가 + 호스팅 단가)
INSERT INTO t_k7f2m9qx4b1t.event_billing (event_id, tenant_id, applied_hourly_price, applied_hosting_hourly_price, estimated_cost) VALUES
    (1001, 'k7f2m9qx4b1t', 25000.00, 2000.00, 189000.00),
    (1002, 'k7f2m9qx4b1t', 40000.00, 3000.00, 258000.00),
    (1003, 'k7f2m9qx4b1t', 10000.00, 1000.00, 55000.00),
    (1004, 'k7f2m9qx4b1t', 25000.00, 2000.00, 162000.00),
    (1005, 'k7f2m9qx4b1t', 10000.00, 1000.00, 66000.00);

INSERT INTO t_k7f2m9qx4b1t.event_status_log (event_id, tenant_id, status_type, prev_status, new_status, changed_at, detail, changed_by) VALUES
    (1001, 'k7f2m9qx4b1t', 'APPLICATION', NULL, 'APPLYING', '2026-10-10 09:00:00', NULL, NULL),
    (1001, 'k7f2m9qx4b1t', 'APPLICATION', 'APPLYING', 'APPLIED', '2026-10-10 09:00:05', NULL, NULL),
    (1002, 'k7f2m9qx4b1t', 'APPLICATION', NULL, 'APPLYING', '2026-10-05 14:00:00', NULL, NULL),
    (1002, 'k7f2m9qx4b1t', 'APPLICATION', 'APPLYING', 'APPLIED', '2026-10-05 14:00:04', NULL, NULL),
    (1002, 'k7f2m9qx4b1t', 'APPLICATION', 'APPLIED', 'APPROVED', '2026-10-06 10:00:00', NULL, 1),
    (1003, 'k7f2m9qx4b1t', 'APPLICATION', NULL, 'APPLYING', '2026-09-10 11:00:00', NULL, NULL),
    (1003, 'k7f2m9qx4b1t', 'APPLICATION', 'APPLYING', 'APPLIED', '2026-09-10 11:00:03', NULL, NULL),
    (1003, 'k7f2m9qx4b1t', 'APPLICATION', 'APPLIED', 'APPROVED', '2026-09-11 09:00:00', NULL, 1),
    (1003, 'k7f2m9qx4b1t', 'HOSTING', 'PENDING', 'HOSTING', '2026-09-25 08:00:00', NULL, NULL),
    (1003, 'k7f2m9qx4b1t', 'EVENT', 'SCHEDULED', 'OPEN', '2026-09-25 10:00:00', NULL, NULL),
    (1003, 'k7f2m9qx4b1t', 'EVENT', 'OPEN', 'EXPIRED', '2026-09-25 11:00:00', NULL, NULL),
    (1003, 'k7f2m9qx4b1t', 'HOSTING', 'HOSTING', 'ENDED', '2026-09-25 13:00:00', NULL, NULL),
    (1004, 'k7f2m9qx4b1t', 'APPLICATION', NULL, 'APPLYING', '2026-10-12 16:00:00', NULL, NULL),
    (1004, 'k7f2m9qx4b1t', 'APPLICATION', 'APPLYING', 'APPLIED', '2026-10-12 16:00:04', NULL, NULL),
    (1004, 'k7f2m9qx4b1t', 'APPLICATION', 'APPLIED', 'REJECTED', '2026-10-13 10:00:00', '이벤트 기간에 정기 점검이 있어 날짜를 옮겨 다시 신청해 주세요.', 1),
    (1005, 'k7f2m9qx4b1t', 'APPLICATION', NULL, 'APPLYING', '2026-10-15 13:00:00', NULL, NULL),
    (1005, 'k7f2m9qx4b1t', 'APPLICATION', 'APPLYING', 'APPLY_FAILED', '2026-10-15 13:05:00', '배너 업로드 실패', NULL);
