-- 스킨 전용 컬럼을 코스메틱 공통 컬럼으로 일반화 (스킨/착수음 등 공유).
ALTER TABLE items CHANGE COLUMN skin_config item_config JSON NULL;
