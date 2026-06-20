-- ============================================================
-- V5: Room/Game 분리 및 GamePlayer 테이블 추가
-- ============================================================

-- 1. rooms 테이블 생성
CREATE TABLE rooms (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_code           VARCHAR(10)  NOT NULL UNIQUE,
    host_id             BIGINT       NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'WAITING',
    game_type           VARCHAR(20)  NOT NULL DEFAULT 'CLASSIC',
    time_limit          VARCHAR(20)  NOT NULL DEFAULT 'UNLIMITED',
    byoyomi_option      VARCHAR(20)  NOT NULL DEFAULT 'NONE',
    max_spectators      INT          NOT NULL DEFAULT 3,
    current_game_number INT          NOT NULL DEFAULT 0,
    created_at          DATETIME(6)  NOT NULL,
    INDEX idx_rooms_status (status),
    INDEX idx_rooms_host (host_id),
    FOREIGN KEY (host_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. 기존 games 데이터를 rooms로 마이그레이션
INSERT INTO rooms (room_code, host_id, status, game_type, time_limit, byoyomi_option, max_spectators, current_game_number, created_at)
SELECT
    g.room_code,
    COALESCE(g.black_player_id, g.white_player_id),
    CASE
        WHEN g.status = 'WAITING'     THEN 'WAITING'
        WHEN g.status = 'IN_PROGRESS' THEN 'IN_PROGRESS'
        ELSE 'CLOSED'
    END,
    'CLASSIC',
    'UNLIMITED',
    'NONE',
    3,
    CASE WHEN g.status = 'WAITING' THEN 0 ELSE 1 END,
    g.created_at
FROM games g
WHERE g.black_player_id IS NOT NULL OR g.white_player_id IS NOT NULL;

-- 3. game_players 테이블 생성
CREATE TABLE game_players (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id           BIGINT       NOT NULL,
    user_id           BIGINT       NOT NULL,
    role              VARCHAR(20)  NOT NULL,
    color             VARCHAR(10),
    remaining_seconds INT,
    in_byoyomi        BOOLEAN      NOT NULL DEFAULT FALSE,
    joined_at         DATETIME(6)  NOT NULL,
    INDEX idx_game_players_room (room_id),
    INDEX idx_game_players_user (user_id),
    UNIQUE KEY uk_game_players_room_user (room_id, user_id),
    FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. black_player → HOST로 마이그레이션
INSERT INTO game_players (room_id, user_id, role, color, remaining_seconds, in_byoyomi, joined_at)
SELECT r.id, g.black_player_id, 'HOST', 'BLACK', NULL, FALSE, g.created_at
FROM games g
JOIN rooms r ON r.room_code = g.room_code
WHERE g.black_player_id IS NOT NULL;

-- 5. white_player → PLAYER로 마이그레이션
INSERT INTO game_players (room_id, user_id, role, color, remaining_seconds, in_byoyomi, joined_at)
SELECT r.id, g.white_player_id, 'PLAYER', 'WHITE', NULL, FALSE, COALESCE(g.started_at, g.created_at)
FROM games g
JOIN rooms r ON r.room_code = g.room_code
WHERE g.white_player_id IS NOT NULL;

-- 6. games 테이블에 room_id, game_number 컬럼 추가 (nullable로 먼저 추가)
ALTER TABLE games ADD COLUMN room_id      BIGINT AFTER id;
ALTER TABLE games ADD COLUMN game_number  INT    NOT NULL DEFAULT 1 AFTER room_id;
ALTER TABLE games ADD COLUMN last_move_at DATETIME(6) AFTER finished_at;

-- 7. room_id 값 채우기
UPDATE games g
JOIN rooms r ON r.room_code = g.room_code
SET g.room_id = r.id;

-- 8. NOT NULL 제약 추가 및 FK 설정
ALTER TABLE games MODIFY COLUMN room_id BIGINT NOT NULL;
ALTER TABLE games ADD CONSTRAINT fk_games_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE;
ALTER TABLE games ADD INDEX idx_games_room_id (room_id);

-- 9. games 테이블에서 room_code 컬럼 제거
ALTER TABLE games DROP COLUMN room_code;
