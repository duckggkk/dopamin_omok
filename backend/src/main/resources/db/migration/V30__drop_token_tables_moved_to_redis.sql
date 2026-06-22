-- 이메일 인증코드 / Refresh 토큰을 Redis(TTL 자동 만료)로 이전했다.
-- "잠깐 살았다 사라지는 데이터"라 영구 DB 테이블이 불필요 → 제거한다.
-- ⚠️ 운영 적용 시: 진행 중이던 이메일 인증은 재요청이, 기존 로그인 세션은 재로그인이 한 번 필요하다(정상).
DROP TABLE IF EXISTS email_verification_tokens;
DROP TABLE IF EXISTS refresh_tokens;
