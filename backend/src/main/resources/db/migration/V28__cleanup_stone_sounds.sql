-- 착수음 정리: 실제 오디오 파일이 없는 착수음(wood/clack/electronic) 제거.
-- 실제 파일이 존재하는 착수음만 남긴다 → default(기본음, default.m4a) · iron(강철, iron.wav).

-- 1) 실제 파일(iron.wav)이 있는 '강철' 착수음 추가 (가챠 풀 대상: default_grant=FALSE)
--    id는 auto-increment에 맡겨 기존 시드 id와 충돌을 피한다.
INSERT INTO items (name, item_type, description, item_config, default_grant)
VALUES ('iron', 'STONE_SOUND', '묵직한 강철 소리',
        JSON_OBJECT('displayName', '강철', 'assetKey', 'iron'), FALSE);

-- 2) 오디오 파일이 없는 착수음 제거 (wood=8, clack=9, electronic=10)
--    자식 행(보유/장착)을 먼저 지워 FK 제약을 만족시킨다.
DELETE FROM user_active_items WHERE item_id IN (8, 9, 10);
DELETE FROM user_items WHERE item_id IN (8, 9, 10);
DELETE FROM items WHERE id IN (8, 9, 10);

-- 3) 위 삭제로 착수음 슬롯이 빈 유저는 기본 착수음(11)으로 되돌린다.
INSERT INTO user_active_items (user_id, item_type, item_id)
SELECT u.id, 'STONE_SOUND', 11 FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM user_active_items ua WHERE ua.user_id = u.id AND ua.item_type = 'STONE_SOUND'
);
