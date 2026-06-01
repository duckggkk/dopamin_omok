-- 착수음 아이템 (오디오 기반, assetKey 사용).
-- 오디오 파일은 classpath:assets/stone_sound/{assetKey}.{mp3|ogg|webm|wav} 에 위치.
-- 새 착수음 추가 시 이 형식으로 행 1개 + 오디오 파일 1개만 추가하면 코드 변경 불필요.
INSERT INTO items (id, name, item_type, description, item_config) VALUES
(8, 'wood',       'STONE_SOUND', '나무 두는 소리',
 JSON_OBJECT('displayName', '나무', 'assetKey', 'wood')),
(9, 'clack',      'STONE_SOUND', '경쾌한 딱 소리',
 JSON_OBJECT('displayName', '딱돌', 'assetKey', 'clack')),
(10, 'electronic', 'STONE_SOUND', '전자음 효과',
 JSON_OBJECT('displayName', '전자음', 'assetKey', 'electronic'));
