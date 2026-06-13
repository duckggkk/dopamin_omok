-- 레이팅(ELO) 점수: 모든 유저는 1000점에서 시작. 승/패/무 시 서버가 ELO로 가감한다.
-- 대국 로비의 '내 레이팅대 추천' 필터가 방장(host)의 rating 으로 비슷한 상대를 골라준다.
ALTER TABLE users ADD COLUMN rating INT NOT NULL DEFAULT 1000;

-- 카드 오목(CARD) 모드 제거: 남아있는 방을 일반(CLASSIC)으로 전환해 enum 매핑 오류를 막는다.
UPDATE rooms SET game_type = 'CLASSIC' WHERE game_type = 'CARD';
