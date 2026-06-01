-- 대리석 바둑판 스킨 추가 (이미지 기반 스킨, assetKey 사용).
-- 텍스처는 classpath:skins/white_marble.webp 에서 인증 후 제공된다.
-- filter 없이 assetKey만 사용 → 프론트는 보호 이미지를 받아 <image>로 렌더.
INSERT INTO items (id, name, item_type, description, skin_config) VALUES
(7, 'white_marble', 'BOARD_SKIN', '하얀 대리석 바둑판',
 JSON_OBJECT(
     'displayName', '대리석',
     'colors', JSON_OBJECT('bg', '#ece9e2', 'lines', '#8a8480', 'dots', '#6a6460'),
     'assetKey', 'white_marble'
 ));
