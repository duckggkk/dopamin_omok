-- 피지컬 오목 리플레이 기록.
-- 실시간 액션이라 일반 game_moves 와 달리, 보드 칸 변화(착수/제거/분화구) 이벤트 스트림을 JSON 으로 보관한다.
-- 경기 종료 시 1회만 INSERT(메모리 버퍼 → 종료 시 flush)되므로 DB 부하는 게임당 1행이다.
CREATE TABLE physical_game_records (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_id    BIGINT NOT NULL,
    replay     JSON   NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_pgr_game (game_id),
    CONSTRAINT fk_pgr_game FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
