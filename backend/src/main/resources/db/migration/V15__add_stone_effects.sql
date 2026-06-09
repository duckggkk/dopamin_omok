-- 착수 효과(STONE_EFFECT): 바둑알 색과 무관한 착수 애니메이션. 절차적(effect 키만).
-- 적용 효과는 서버가 user_active_items 에서 읽어 방 상태(GamePlayerResponse.stoneEffect)로 내려주므로
-- 미보유자가 사용할 수 없다(유료재화 보호). 기본 돌은 효과 없이 그냥 착수된다.
-- effect 키는 백엔드 검증기 화이트리스트(현재 'bounce')와 프론트 렌더가 합의한 값만 허용.
-- default_grant = FALSE → 가챠 풀(STONE_EFFECT 상자)에 자동 포함.
INSERT INTO items (id, name, item_type, description, item_config, default_grant) VALUES
(15, 'bounce', 'STONE_EFFECT', '돌이 통통 튀며 놓이는 효과',
 JSON_OBJECT('displayName', '뽀잉', 'effect', 'bounce'), FALSE);

-- 고급 바둑알 스킨은 effect를 번들할 수 있다(STONE_EFFECT 미보유여도 적용).
-- 루비돌(id 14)을 '뽀잉' 애니메이션 포함 고급 스킨으로 승격.
UPDATE items
SET item_config = JSON_SET(item_config, '$.effect', 'bounce')
WHERE id = 14 AND item_type = 'STONE_SKIN';
