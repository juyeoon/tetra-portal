-- =====================================================================
-- 공용 시드: 요금제(traffic_plan) · 이벤트 앱 템플릿(template)
-- 결정 (2026-10-19, Portal 계획서 Q3 · Q12)
--  - 요금제 4종 S · M · L · XL 모두 노출 (XL 포함)
--  - 수용 인원은 화면 설계(Portal web planMeta)와 같은 값
--  - 단가는 임시 값이다. 화면 · API에 금액을 표시하지 않기로 했고(Portal 응답에서 뺌), DB에만 두어 신청 때
--    event_billing에 그대로 기록된다. 실제 단가가 정해지면 새 V 파일로 UPDATE 한다(이 파일은 고치지 않는다).
--  - 템플릿은 쿠폰 이벤트 앱 하나. 템플릿이 없는 타입(TIMESALE · LIVE_COMMERCE)은 신청할 수 없다
--    (file_path = 템플릿 버킷 tetra-templates-{env}의 앱 빌드 경로 — 플랜 40-edge · WF1-2)
-- 재실행 안전: 이미 있으면 값을 덮어쓴다(dev에 손으로 넣은 행이 있어도 실패하지 않게)
-- =====================================================================

INSERT INTO traffic_plan (plan_code, max_capacity, hourly_price, hosting_hourly_price, is_visible) VALUES
    ('S',  10000,  10000.00, 1000.00, 1),
    ('M',  30000,  25000.00, 2000.00, 1),
    ('L',  50000,  40000.00, 3000.00, 1),
    ('XL', 100000, 70000.00, 5000.00, 1)
AS new
ON DUPLICATE KEY UPDATE
    max_capacity         = new.max_capacity,
    hourly_price         = new.hourly_price,
    hosting_hourly_price = new.hosting_hourly_price,
    is_visible           = new.is_visible;

INSERT INTO template (event_type, description, file_path) VALUES
    ('COUPON', '쿠폰 이벤트 앱', 'apps/coupon/')
AS new
ON DUPLICATE KEY UPDATE
    description = new.description,
    file_path   = new.file_path;
