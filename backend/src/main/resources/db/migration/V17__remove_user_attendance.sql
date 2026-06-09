-- 출석 보상 기능 제거 — V16에서 추가한 컬럼을 드롭한다.
-- (V16은 이미 적용되어 불변이므로 수정하지 않고 정방향 마이그레이션으로 되돌린다.)
ALTER TABLE users DROP COLUMN last_attendance_date;
