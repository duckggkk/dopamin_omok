CREATE TABLE IF NOT EXISTS game_moves (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_id     BIGINT       NOT NULL,
    player_id   BIGINT       NOT NULL,
    color       VARCHAR(10)  NOT NULL,
    row_pos     INT          NOT NULL,
    col         INT          NOT NULL,
    move_number INT          NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    INDEX idx_game_moves_game_id (game_id),
    INDEX idx_game_moves_game_seq (game_id, move_number),
    FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE,
    FOREIGN KEY (player_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
