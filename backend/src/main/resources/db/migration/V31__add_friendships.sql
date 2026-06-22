-- 친구 관계(요청/수락). 한 쌍당 한 행(방향 무관 중복은 애플리케이션에서 차단).
CREATE TABLE IF NOT EXISTS friendships (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    requester_id BIGINT       NOT NULL,
    addressee_id BIGINT       NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    UNIQUE KEY uk_friendship_pair (requester_id, addressee_id),
    INDEX idx_friendship_requester (requester_id),
    INDEX idx_friendship_addressee (addressee_id),
    FOREIGN KEY (requester_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (addressee_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
