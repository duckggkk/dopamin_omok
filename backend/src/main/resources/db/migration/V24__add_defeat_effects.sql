-- 패배 이펙트(DEFEAT_EFFECT): 승자가 장착하면 '패자 화면'에 뜨는 연출. 절차적(effect 키만).
-- 기본 패배는 이펙트 없이 문구만 표시되며, 승자가 이 아이템을 장착했을 때만 패자 화면에 연출이 뜬다.
-- 적용 효과는 서버가 user_active_items 에서 읽어 게임 결과로 내려주므로 미보유자가 쓸 수 없다(유료재화 보호).
-- effect 키는 백엔드 검증기 화이트리스트(flame/shatter/storm/tears)와 프론트 렌더가 합의한 값만 허용.
-- default_grant = FALSE → 가챠 풀(DEFEAT_EFFECT 상자)에 자동 포함.
INSERT INTO items (name, item_type, description, item_config, default_grant) VALUES
('flame',   'DEFEAT_EFFECT', '패자 화면을 불길로 뒤덮는 연출',   JSON_OBJECT('displayName', '불태우기', 'effect', 'flame'),   FALSE),
('shatter', 'DEFEAT_EFFECT', '패자 화면이 와장창 깨지는 연출',   JSON_OBJECT('displayName', '와장창',   'effect', 'shatter'), FALSE),
('storm',   'DEFEAT_EFFECT', '먹구름과 번개가 몰아치는 연출',     JSON_OBJECT('displayName', '먹구름',   'effect', 'storm'),   FALSE),
('tears',   'DEFEAT_EFFECT', '눈물이 주룩주룩 흐르는 연출',       JSON_OBJECT('displayName', '눈물바다', 'effect', 'tears'),   FALSE);
