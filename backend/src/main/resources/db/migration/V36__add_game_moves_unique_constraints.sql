-- 동시 착수로 생기는 중복 수순·중복 좌표를 DB 차원에서 차단한다.
--
-- 배경: GameService.placeStone 은 "기존 수순 조회 → 중복/턴 검사 → moveNumber 계산 → 저장"
--       순으로 동작하는데, 이 구간에 락이 없어서 같은 게임에 대한 동시 요청 두 건이
--       같은 스냅샷을 읽고 둘 다 통과할 수 있었다(한 턴에 돌 2개 · 승패 이중 반영).
--       애플리케이션에는 비관적 락(SELECT ... FOR UPDATE)을 걸었고,
--       이 마이그레이션은 그 뒤를 받치는 2차 방어선이다.
--
-- 이 테이블은 클래식 오목 전용이다. 피지컬 오목은 돌이 파괴·재배치되지만
-- physical_game_records 를 따로 쓰므로 좌표 유니크 제약의 영향을 받지 않는다.

-- ── 1) 기존 데이터 정리 ──────────────────────────────────────────────────────
-- 이미 중복이 들어가 있으면 유니크 인덱스 생성이 실패하고,
-- Flyway 가 실패하면 애플리케이션이 아예 기동하지 않는다(= 배포 장애).
-- 따라서 제약을 걸기 전에 먼저 정리한다.
-- 정리 기준: 같은 키를 가진 행 중 id 가 가장 작은 것(먼저 저장된 것) 하나만 남긴다.

-- 같은 게임에 중복된 move_number 제거
DELETE dup FROM game_moves dup
INNER JOIN game_moves keep
        ON dup.game_id     = keep.game_id
       AND dup.move_number = keep.move_number
       AND dup.id          > keep.id;

-- 같은 게임에 중복된 좌표 제거
DELETE dup FROM game_moves dup
INNER JOIN game_moves keep
        ON dup.game_id = keep.game_id
       AND dup.row_pos = keep.row_pos
       AND dup.col     = keep.col
       AND dup.id      > keep.id;

-- ── 2) 유니크 인덱스 생성 ────────────────────────────────────────────────────
-- (game_id, move_number) 유니크 인덱스가 V3 의 비유니크 인덱스를 완전히 대체하므로
-- 기존 것은 제거한다(같은 컬럼·같은 순서라 중복 인덱스가 된다).
DROP INDEX idx_game_moves_game_seq ON game_moves;

CREATE UNIQUE INDEX uk_game_moves_game_seq ON game_moves (game_id, move_number);
CREATE UNIQUE INDEX uk_game_moves_game_pos ON game_moves (game_id, row_pos, col);
