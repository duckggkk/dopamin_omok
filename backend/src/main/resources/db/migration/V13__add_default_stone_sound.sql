-- 기본 아이템 플래그: 가입 시 전원 자동 지급, 뽑기 풀에서는 제외.
ALTER TABLE items ADD COLUMN default_grant BOOLEAN NOT NULL DEFAULT FALSE;

-- 기본 착수음 (모든 유저 기본 제공).
-- 오디오 파일: classpath:assets/stone_sound/default.{mp3|ogg|webm|wav}
INSERT INTO items (id, name, item_type, description, item_config, default_grant) VALUES
(11, 'default', 'STONE_SOUND', '기본 착수음',
 JSON_OBJECT('displayName', '기본음', 'assetKey', 'default'), TRUE);

-- 기존 전체 유저에게 기본 착수음 지급 (미보유자만)
INSERT INTO user_items (user_id, item_id, acquired_at)
SELECT u.id, 11, NOW(6) FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM user_items ui WHERE ui.user_id = u.id AND ui.item_id = 11
);

-- STONE_SOUND 활성 슬롯이 없는 유저는 기본 착수음 장착
INSERT INTO user_active_items (user_id, item_type, item_id)
SELECT u.id, 'STONE_SOUND', 11 FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM user_active_items ua WHERE ua.user_id = u.id AND ua.item_type = 'STONE_SOUND'
);
