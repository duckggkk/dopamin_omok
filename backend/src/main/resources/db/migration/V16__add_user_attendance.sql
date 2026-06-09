-- 출석 보상: 마지막 출석(보상 수령)일을 사용자별로 추적.
-- NULL = 아직 한 번도 수령 안 함. 하루 1회만 수령 가능(서버에서 오늘 날짜와 비교).
ALTER TABLE users ADD COLUMN last_attendance_date DATE NULL;
