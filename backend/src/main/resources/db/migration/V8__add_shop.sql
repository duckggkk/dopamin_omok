ALTER TABLE users ADD COLUMN currency INT NOT NULL DEFAULT 0;

CREATE TABLE items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    item_type VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_items_type (item_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    acquired_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_user_items_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_items_item FOREIGN KEY (item_id) REFERENCES items(id),
    UNIQUE KEY uq_user_item (user_id, item_id),
    INDEX idx_user_items_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_active_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    item_type VARCHAR(50) NOT NULL,
    item_id BIGINT NOT NULL,
    CONSTRAINT fk_active_items_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_active_items_item FOREIGN KEY (item_id) REFERENCES items(id),
    UNIQUE KEY uq_user_item_type (user_id, item_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO items (id, name, item_type, description) VALUES
(1, 'EZ',   'DEFEAT_MESSAGE', 'EZ'),
(2, '쉽다', 'DEFEAT_MESSAGE', '쉽다'),
(3, 'ㅋ',   'DEFEAT_MESSAGE', 'ㅋ'),
(4, 'bamboo', 'BOARD_SKIN', '대나무 바둑판'),
(5, 'dark',   'BOARD_SKIN', '흑판 바둑판'),
(6, 'gold',   'BOARD_SKIN', '금빛 바둑판');
