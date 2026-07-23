-- 사용자 하드 삭제(현재는 게스트 정리·미인증 계정 회수 전용)가
-- '상대방의 대국 기록'까지 끌고 내려가는 FK 연쇄 2건을 끊는다.
--
-- 문제 1: rooms.host_id 가 ON DELETE CASCADE(V5)
--   users 삭제 → 그가 방장이었던 rooms 삭제 → games(room_id CASCADE) 삭제 →
--   game_moves/game_players/physical_game_records 까지 전부 연쇄 삭제.
--   games.black/white_player_id 를 SET NULL(V1)로 설계한 의도("사람이 사라져도 대국은 남긴다")를
--   방장 경로가 우회해 버린다. → SET NULL 로 바꿔 방·대국을 보존한다.
--
-- 문제 2: game_moves.player_id 가 ON DELETE CASCADE(V3)
--   대국 행이 살아남아도 삭제된 사용자가 둔 수만 기보에서 빠져 반쪽짜리 기보가 된다.
--   돌 색(color)은 game_moves 행에 직접 저장돼 있어 착수자 없이도 재생에 문제없다.
--   → SET NULL 로 바꿔 기보 전체를 보존한다.
--
-- (회원 탈퇴는 V37 의 소프트 삭제(익명화)라 이 연쇄를 타지 않는다. 이 마이그레이션은
--  게스트 정리 스케줄러 등 '진짜 DELETE' 경로가 남의 기록을 지우지 않게 하는 것이다.)

-- ── 1) 기존 FK 제거 ──────────────────────────────────────────────────────────
-- V1/V3/V5 는 FK 를 이름 없이 만들어 MySQL 이 자동 명명(rooms_ibfk_1 등)했다.
-- 환경마다 이름이 다를 수 있으므로 information_schema 에서 찾아 동적으로 제거한다.

SET @fk := (SELECT rc.CONSTRAINT_NAME
            FROM information_schema.REFERENTIAL_CONSTRAINTS rc
            WHERE rc.CONSTRAINT_SCHEMA = DATABASE()
              AND rc.TABLE_NAME = 'rooms'
              AND rc.REFERENCED_TABLE_NAME = 'users');
SET @sql := CONCAT('ALTER TABLE rooms DROP FOREIGN KEY `', @fk, '`');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk := (SELECT rc.CONSTRAINT_NAME
            FROM information_schema.REFERENTIAL_CONSTRAINTS rc
            WHERE rc.CONSTRAINT_SCHEMA = DATABASE()
              AND rc.TABLE_NAME = 'game_moves'
              AND rc.REFERENCED_TABLE_NAME = 'users');
SET @sql := CONCAT('ALTER TABLE game_moves DROP FOREIGN KEY `', @fk, '`');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ── 2) SET NULL 이 가능하도록 컬럼을 nullable 로 완화 ────────────────────────
ALTER TABLE rooms MODIFY COLUMN host_id BIGINT NULL;
ALTER TABLE game_moves MODIFY COLUMN player_id BIGINT NULL;

-- ── 3) SET NULL FK 재생성(이번엔 이름을 명시해 다음 변경을 쉽게 한다) ────────
ALTER TABLE rooms
    ADD CONSTRAINT fk_rooms_host FOREIGN KEY (host_id)
    REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE game_moves
    ADD CONSTRAINT fk_game_moves_player FOREIGN KEY (player_id)
    REFERENCES users(id) ON DELETE SET NULL;
