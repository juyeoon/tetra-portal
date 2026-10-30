-- =====================================================================
-- 요금제 XL 숨김 (2026-10-22, Portal·인프라와 합의)
--  - infra-k8s envs/dev/plans.yaml 의 XL 이 API 0 · Redis 0(미정)이라
--    XL 이벤트가 승인되면 WF2-2 에서 Pod 가 뜨지 않는다.
--  - Portal 신청 API 는 is_visible = 1 인 요금제만 받으므로, 숨기면 목록과 신청이 함께 막힌다(코드 변경 없음).
--  - plans.yaml 에 XL 값이 정해지면 새 V 파일로 is_visible = 1 로 되돌린다(이 파일은 고치지 않는다).
-- 재실행 안전: 같은 값으로 UPDATE
-- =====================================================================

UPDATE traffic_plan SET is_visible = 0 WHERE plan_code = 'XL';