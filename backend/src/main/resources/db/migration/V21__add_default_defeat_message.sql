-- 기본 패배 문구 '패배'.
-- default_grant=TRUE → 신규 유저 가입 시 자동 지급·장착되어, 승자가 별도 문구를 장착하지 않아도
-- 패자에게 항상 '패배'가 노출된다.
INSERT INTO items (name, item_type, description, default_grant) VALUES
('패배', 'DEFEAT_MESSAGE', '기본 패배 문구', TRUE);

-- 기존 유저 백필: 모두에게 '패배'를 보유 처리하고, 패배문구를 아직 장착하지 않은 유저에게 자동 장착.
INSERT IGNORE INTO user_items (user_id, item_id)
SELECT u.id, i.id
FROM users u
CROSS JOIN (SELECT id FROM items WHERE name = '패배' AND item_type = 'DEFEAT_MESSAGE' LIMIT 1) i;

INSERT IGNORE INTO user_active_items (user_id, item_type, item_id)
SELECT u.id, 'DEFEAT_MESSAGE', i.id
FROM users u
CROSS JOIN (SELECT id FROM items WHERE name = '패배' AND item_type = 'DEFEAT_MESSAGE' LIMIT 1) i
WHERE NOT EXISTS (
    SELECT 1 FROM user_active_items a
    WHERE a.user_id = u.id AND a.item_type = 'DEFEAT_MESSAGE'
);
