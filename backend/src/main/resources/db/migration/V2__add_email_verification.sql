-- users 테이블에 이메일 인증 필드 추가
ALTER TABLE users
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE AFTER provider_id;

-- 소셜 로그인 사용자는 이미 인증된 것으로 처리
UPDATE users SET email_verified = TRUE WHERE provider != 'LOCAL';

-- 이메일 인증 토큰 테이블
CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT          NOT NULL,
    token       VARCHAR(100)    NOT NULL UNIQUE,
    expires_at  DATETIME(6)     NOT NULL,
    created_at  DATETIME(6)     NOT NULL,
    INDEX idx_evt_user_id (user_id),
    INDEX idx_evt_token (token),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
