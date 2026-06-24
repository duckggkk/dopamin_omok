-- 피지컬 오목 'AI 연습' 상대로 쓰는 예약 봇 계정.
-- 로그인 불가(password NULL), role=BOT. 봇과의 대국은 RoomService 에서 승패·레이팅 기록을 건너뛰므로
-- 봇은 항상 0판으로 남아 랭킹(전적>0 필터)에도 노출되지 않는다. 모든 AI 연습 대국이 이 한 계정을 공유한다.
INSERT INTO users (public_id, email, nickname, role, provider, email_verified, created_at, updated_at)
VALUES (UNHEX('00000000000000000000000000000B07'),
        'ai-practice-bot@dopamin.local', 'AI 연습봇',
        'BOT', 'LOCAL', TRUE, NOW(6), NOW(6));
