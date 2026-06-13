-- 레이팅을 모드별로 분리한다.
-- 기존 단일 rating 을 일반(classic) 레이팅으로 보존하고, 피지컬 레이팅 컬럼을 1000점 기준으로 새로 추가.
ALTER TABLE users CHANGE COLUMN rating classic_rating INT NOT NULL DEFAULT 1000;
ALTER TABLE users ADD COLUMN physical_rating INT NOT NULL DEFAULT 1000;
