ALTER TABLE items ADD COLUMN skin_config JSON NULL;

-- 바둑판 스킨 메타데이터 (색상 + SVG 필터 파라미터).
-- 새 스킨 추가 시 이 형식으로 INSERT 한 행만 추가하면 백엔드/프론트 코드 변경 불필요.
UPDATE items SET skin_config = JSON_OBJECT(
    'displayName', '대나무',
    'colors', JSON_OBJECT('bg', '#6a9a5a', 'lines', '#3a6a2a', 'dots', '#3a6a2a'),
    'filter', JSON_OBJECT('type', 'fractalNoise', 'freqX', 0.03, 'freqY', 0.8, 'octaves', 3, 'seed', 7, 'blend', 'soft-light')
) WHERE id = 4;

UPDATE items SET skin_config = JSON_OBJECT(
    'displayName', '흑판',
    'colors', JSON_OBJECT('bg', '#323248', 'lines', '#6868a0', 'dots', '#6868a0'),
    'filter', JSON_OBJECT('type', 'turbulence', 'freqX', 0.5, 'freqY', 0.45, 'octaves', 6, 'seed', 18, 'blend', 'overlay')
) WHERE id = 5;

UPDATE items SET skin_config = JSON_OBJECT(
    'displayName', '금빛',
    'colors', JSON_OBJECT('bg', '#b08818', 'lines', '#705500', 'dots', '#705500'),
    'filter', JSON_OBJECT('type', 'fractalNoise', 'freqX', 0.85, 'freqY', 0.04, 'octaves', 5, 'seed', 11, 'blend', 'overlay')
) WHERE id = 6;
