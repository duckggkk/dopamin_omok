-- 피지컬 오목 캐릭터 스킨 아이템 (CHARACTER_SKIN, 절차적 — 색상 + face 키워드).
-- 적용 캐릭터는 서버가 user_active_items 에서 읽어 스냅샷으로 내려주므로 미보유자가 쓸 수 없다(유료재화 보호).
-- item_config.character = { body(몸 색 hex), accent(테두리 색 hex), face(프론트 이모지 매핑 키워드) }.
-- face 는 4바이트 이모지를 DB에 넣지 않기 위해 안전 키워드로 저장하고 프론트가 이모지로 매핑한다.
-- default_grant = FALSE → 가챠 풀(CHARACTER_SKIN 상자)에 자동 포함.
INSERT INTO items (id, name, item_type, description, item_config, default_grant) VALUES
(16, 'robot_character', 'CHARACTER_SKIN', '강철빛 로봇 캐릭터',
 JSON_OBJECT('displayName', '로봇',
     'character', JSON_OBJECT('body', '#7fb3d5', 'accent', '#2c3e50', 'face', 'robot')), FALSE),
(17, 'rabbit_character', 'CHARACTER_SKIN', '깡총거리는 토끼 캐릭터',
 JSON_OBJECT('displayName', '토끼',
     'character', JSON_OBJECT('body', '#f7d6e0', 'accent', '#c97b9a', 'face', 'rabbit')), FALSE),
(18, 'ghost_character', 'CHARACTER_SKIN', '장난꾸러기 유령 캐릭터',
 JSON_OBJECT('displayName', '유령',
     'character', JSON_OBJECT('body', '#e3e8ef', 'accent', '#8895a7', 'face', 'ghost')), FALSE);
