-- 데이터베이스 생성 (수동 실행)
-- CREATE DATABASE dopamin_omok CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    email           VARCHAR(100)  NOT NULL UNIQUE,
    password        VARCHAR(200),
    nickname        VARCHAR(30)   NOT NULL UNIQUE,
    role            VARCHAR(20)   NOT NULL DEFAULT 'USER',
    provider        VARCHAR(20)   NOT NULL DEFAULT 'LOCAL',
    provider_id     VARCHAR(200),
    profile_image_url VARCHAR(500),
    wins            INT           NOT NULL DEFAULT 0,
    losses          INT           NOT NULL DEFAULT 0,
    draws           INT           NOT NULL DEFAULT 0,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    INDEX idx_users_email (email),
    INDEX idx_users_nickname (nickname)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT          NOT NULL,
    token       VARCHAR(500)    NOT NULL UNIQUE,
    expires_at  DATETIME(6)     NOT NULL,
    created_at  DATETIME(6)     NOT NULL,
    INDEX idx_refresh_tokens_user_id (user_id),
    INDEX idx_refresh_tokens_token (token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS games (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    black_player_id BIGINT,
    white_player_id BIGINT,
    winner_id       BIGINT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'WAITING',
    current_turn    VARCHAR(10),
    room_code       VARCHAR(10)  NOT NULL,
    board_size      INT          NOT NULL DEFAULT 15,
    created_at      DATETIME(6)  NOT NULL,
    started_at      DATETIME(6),
    finished_at     DATETIME(6),
    INDEX idx_games_black_player (black_player_id),
    INDEX idx_games_white_player (white_player_id),
    INDEX idx_games_status (status),
    FOREIGN KEY (black_player_id) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY (white_player_id) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY (winner_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS game_moves (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_id     BIGINT          NOT NULL,
    player_id   BIGINT          NOT NULL,
    color       VARCHAR(10)     NOT NULL,
    row_pos     INT             NOT NULL COMMENT 'using row_pos to avoid reserved word',
    col         INT             NOT NULL,
    move_number INT             NOT NULL,
    created_at  DATETIME(6)     NOT NULL,
    INDEX idx_game_moves_game_id (game_id),
    INDEX idx_game_moves_game_seq (game_id, move_number),
    FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE,
    FOREIGN KEY (player_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
