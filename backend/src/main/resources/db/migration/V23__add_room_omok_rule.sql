-- 방 단위 오목 규칙 변형(자유룰/렌주룰). 기존 방은 모두 자유룰로 백필.
-- 렌주룰은 클래식 오목에서 흑(선)에게 금수(3-3·4-4·장목)를 적용한다. 피지컬은 항상 FREESTYLE.
ALTER TABLE rooms ADD COLUMN omok_rule VARCHAR(20) NOT NULL DEFAULT 'FREESTYLE';
