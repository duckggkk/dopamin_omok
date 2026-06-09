-- 바둑알 스킨 아이템 (STONE_SKIN, 절차적 — 색상만 사용).
-- 별도 에셋 파일/소유권 면제 불필요: 적용 스킨은 서버가 user_active_items 에서 읽어
-- 방 상태(GamePlayerResponse.stoneSkin)로 전원에게 내려주므로 미보유자가 사용할 수 없다(유료재화 보호).
-- item_config.stone = { fill(채움), stroke(테두리), shine(광택 하이라이트) }, 모두 hex 색상.
-- 새 바둑알 스킨 추가 시 이 형식으로 행 1개만 추가하면 코드 변경 불필요.
-- default_grant = FALSE → 가챠 풀(STONE_SKIN 상자)에 자동 포함.
INSERT INTO items (id, name, item_type, description, item_config, default_grant) VALUES
(12, 'gold_stone', 'STONE_SKIN', '황금빛으로 빛나는 바둑알',
 JSON_OBJECT('displayName', '금돌',
     'stone', JSON_OBJECT('fill', '#d4af37', 'stroke', '#8a6d1b', 'shine', '#fff3c4')), FALSE),
(13, 'jade_stone', 'STONE_SKIN', '맑은 비취빛 바둑알',
 JSON_OBJECT('displayName', '옥돌',
     'stone', JSON_OBJECT('fill', '#3fa66a', 'stroke', '#1f5e3a', 'shine', '#d6ffe6')), FALSE),
(14, 'ruby_stone', 'STONE_SKIN', '붉게 타오르는 루비 바둑알',
 JSON_OBJECT('displayName', '루비돌',
     'stone', JSON_OBJECT('fill', '#c0314b', 'stroke', '#7a1226', 'shine', '#ffd2dc')), FALSE);
