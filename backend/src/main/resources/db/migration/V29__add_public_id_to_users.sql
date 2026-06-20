-- ============================================================
-- V29: users.public_id (외부 노출용 UUID 식별자) 컬럼 추가
-- ------------------------------------------------------------
-- User 엔티티(User.publicId)는 BINARY(16) NOT NULL UNIQUE 컬럼을 기대하는데
-- 이를 만드는 마이그레이션이 누락돼 있어, prod(ddl-auto:validate)에서
-- "missing column [public_id] in table [users]" 로 기동이 실패했다.
-- 엔티티 정의에 맞춰 컬럼을 추가한다.
-- ============================================================

-- 1) nullable 로 먼저 추가 (기존 행이 있어도 안전하게)
ALTER TABLE users ADD COLUMN public_id BINARY(16) NULL AFTER id;

-- 2) 기존 행에 무작위 UUID 백필 (신규 DB면 0행이라 무영향)
UPDATE users SET public_id = UUID_TO_BIN(UUID()) WHERE public_id IS NULL;

-- 3) NOT NULL + 유니크 인덱스 (엔티티의 nullable=false, unique=true 대응)
ALTER TABLE users MODIFY COLUMN public_id BINARY(16) NOT NULL;
ALTER TABLE users ADD UNIQUE KEY uk_users_public_id (public_id);
