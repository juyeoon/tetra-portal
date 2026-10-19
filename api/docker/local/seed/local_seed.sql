-- 로컬 개발 전용 seed (tetra_platform). 운영 값이 아니다.
-- 승인 · 반려 이벤트의 processed_by(fk_event_operator)용 운영자. Portal은 읽지 않는다
INSERT INTO operator (operator_id, email, contact_name, password) VALUES
    (1, 'operator@tetra-operation.com', '홍길동', '$2a$10$okElfhEuxbaHWkowSCIryez9gAIoLsJ78Jxo9f/j1vYHXyif9Jlhu');

-- 요금제 · 템플릿은 db/migration/platform/V2(db-schema 시드)가 넣는다 — 금액은 화면 · API에 나오지 않는다(2026-10-19)

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

-- ---------------------------------------------------------------- 1003(추석 감사 쿠폰, 종료)의 결과 — 결과 탭 확인용 (state = READY)
-- 집계 Job(발급 API)이 넣는 지표 + 발급 이력 저장 API가 채우는 값. CSV 경로는 키만(계획서 Q8)
INSERT INTO t_k7f2m9qx4b1t.event_result_summary (event_id, tenant_id, session_count, ticket_count, peak_concurrent, peak_at, metrics_finalized_at,
    total_stock, success_count, soldout_count, sellout_rate, history_file_path, history_generated_at) VALUES
    (1003, 'k7f2m9qx4b1t', 14, 12, 9, '2026-09-25 10:01:00', '2026-09-25 11:10:00', 10000, 8, 2, 0.0008, 'issuance-history/1003.csv', '2026-09-25 13:10:00');

INSERT INTO t_k7f2m9qx4b1t.issuance_history (event_id, tenant_id, user_id, ticket_number, session_issued_at, queue_entered_at, issued_at, result) VALUES
    (1003, 'k7f2m9qx4b1t', 'user-001', 1, '2026-09-25 09:59:50.000', '2026-09-25 10:00:01.120', '2026-09-25 10:00:01.300', 'SUCCESS'),
    (1003, 'k7f2m9qx4b1t', 'user-002', 2, '2026-09-25 09:59:52.000', '2026-09-25 10:00:01.450', '2026-09-25 10:00:01.600', 'SUCCESS'),
    (1003, 'k7f2m9qx4b1t', 'user-003', 3, '2026-09-25 09:59:55.000', '2026-09-25 10:00:02.010', '2026-09-25 10:00:02.200', 'SUCCESS'),
    (1003, 'k7f2m9qx4b1t', 'user-004', 4, '2026-09-25 09:59:58.000', '2026-09-25 10:00:03.500', '2026-09-25 10:00:03.700', 'FAILED_SOLDOUT'),
    (1003, 'k7f2m9qx4b1t', 'user-005', 5, '2026-09-25 10:00:20.000', '2026-09-25 10:00:30.000', '2026-09-25 10:00:30.200', 'SUCCESS'),
    (1003, 'k7f2m9qx4b1t', 'user-006', 6, '2026-09-25 10:00:40.000', '2026-09-25 10:01:05.000', '2026-09-25 10:01:05.300', 'SUCCESS'),
    (1003, 'k7f2m9qx4b1t', 'user-007', 7, '2026-09-25 10:00:50.000', '2026-09-25 10:01:10.000', '2026-09-25 10:01:10.200', 'SUCCESS'),
    (1003, 'k7f2m9qx4b1t', 'user-008', 8, '2026-09-25 10:01:20.000', '2026-09-25 10:01:40.000', '2026-09-25 10:01:40.100', 'FAILED_SOLDOUT'),
    (1003, 'k7f2m9qx4b1t', 'user-009', 9, '2026-09-25 10:02:00.000', '2026-09-25 10:02:10.000', '2026-09-25 10:02:10.300', 'SUCCESS'),
    (1003, 'k7f2m9qx4b1t', 'user-010', 10, '2026-09-25 10:03:00.000', '2026-09-25 10:03:30.000', '2026-09-25 10:03:30.200', 'SUCCESS');

-- ---------------------------------------------------------------- 알림 · 리포트 (화면 확인용)
-- 알림은 Console(승인 INFO · 반려 WARNING)과 운영 자동화가 넣는다. category 값은 고도화 때 확정
INSERT INTO t_k7f2m9qx4b1t.tenant_notification (tenant_id, event_id, category, priority, occurred_at, content, is_read) VALUES
    ('k7f2m9qx4b1t', 1003, 'APPLICATION', 'INFO', '2026-09-11 09:00:00', '[추석 감사 쿠폰] 신청이 승인되었습니다.', 1),
    ('k7f2m9qx4b1t', 1002, 'APPLICATION', 'INFO', '2026-10-06 10:00:00', '[가을 정기 세일 쿠폰] 신청이 승인되었습니다.', 0),
    ('k7f2m9qx4b1t', 1004, 'APPLICATION', 'WARNING', '2026-10-13 10:00:00', '[연말 감사 쿠폰] 신청이 반려되었습니다. 사유: 이벤트 기간에 정기 점검이 있어 날짜를 옮겨 다시 신청해 주세요.', 0),
    ('k7f2m9qx4b1t', NULL, 'SYSTEM', 'URGENT', '2026-10-15 09:00:00', '10월 20일 02:00~04:00 정기 점검이 있습니다.', 0);

-- 리포트 파일은 감사 버킷(로컬에는 없음 — 다운로드 링크는 가짜 주소). 누가 만드는지는 확정 필요(계획서 Q6)
INSERT INTO t_k7f2m9qx4b1t.tenant_report_meta (tenant_id, name, period_start, period_end, category, created_at, file_path) VALUES
    ('k7f2m9qx4b1t', '2026년 9월 월간 리포트', '2026-09-01 00:00:00', '2026-09-30 23:59:59', 'MONTHLY', '2026-10-01 06:00:00', 'reports/2026-09.pdf');
