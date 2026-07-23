-- users 의 누적 전적(wins/losses/draws)을 games 기록에서 다시 계산해 채운다.
--
-- 배경: 방의 ranked 플래그가 레이팅·전적 반영 여부를 가르던 동안, 방 만들기가 캐주얼로 고정되면서
-- 종료된 대국이 누적 컬럼에 전혀 반영되지 않았다. 이제 반영 여부는 '회원 대 회원인지'로만 정하므로
-- (Game.isRated) 과거 대국도 같은 규칙으로 다시 세어, 통합 전적과 랭킹이 실제 기록과 맞도록 한다.
-- 이 컬럼이 0이면 랭킹 쿼리의 '한 판이라도 둔 회원' 조건에 걸려 순위에 아예 뜨지 않는다.
--
-- 집계 규칙 — UserGameStatsPersistenceAdapter.statsByMode 와 동일하게 맞춘다.
--   승: status=FINISHED 이고 winner_id = 본인
--   패: status=FINISHED 이고 winner_id = 상대
--   무: status=DRAW
--   제외: ABANDONED(중단된 판), 봇·게스트가 낀 대국
--
-- 증분이 아니라 전체 재계산(SET =)이라 여러 번 실행해도 결과가 같다.
--
-- 알려진 한계 두 가지:
--  1) 탈퇴로 참가자 FK가 NULL 이 된 대국은 상대의 역할(회원/게스트)을 확인할 수 없어 제외된다.
--     그 판이 누적 컬럼에 남아 있었다면 이번 재계산에서 빠진다(모드별 전적 집계와 동일한 동작).
--  2) 레이팅(classic_rating/physical_rating)은 대국 순서에 따라 증감폭이 달라지는 Elo 라
--     단순 재계산이 불가능하므로 건드리지 않는다 — 앞으로의 대국부터 쌓인다.

UPDATE users u
LEFT JOIN (
    SELECT r.user_id,
           SUM(r.win)  AS wins,
           SUM(r.loss) AS losses,
           SUM(r.draw) AS draws
    FROM (
        -- 흑 기준 한 행
        SELECT g.black_player_id AS user_id,
               CASE WHEN g.status = 'FINISHED' AND g.winner_id = g.black_player_id THEN 1 ELSE 0 END AS win,
               CASE WHEN g.status = 'FINISHED' AND g.winner_id = g.white_player_id THEN 1 ELSE 0 END AS loss,
               CASE WHEN g.status = 'DRAW' THEN 1 ELSE 0 END AS draw
        FROM games g
        JOIN users b ON b.id = g.black_player_id
        JOIN users w ON w.id = g.white_player_id
        WHERE g.status IN ('FINISHED', 'DRAW')
          AND b.role NOT IN ('BOT', 'GUEST')
          AND w.role NOT IN ('BOT', 'GUEST')

        UNION ALL

        -- 백 기준 한 행
        SELECT g.white_player_id,
               CASE WHEN g.status = 'FINISHED' AND g.winner_id = g.white_player_id THEN 1 ELSE 0 END,
               CASE WHEN g.status = 'FINISHED' AND g.winner_id = g.black_player_id THEN 1 ELSE 0 END,
               CASE WHEN g.status = 'DRAW' THEN 1 ELSE 0 END
        FROM games g
        JOIN users b ON b.id = g.black_player_id
        JOIN users w ON w.id = g.white_player_id
        WHERE g.status IN ('FINISHED', 'DRAW')
          AND b.role NOT IN ('BOT', 'GUEST')
          AND w.role NOT IN ('BOT', 'GUEST')
    ) r
    GROUP BY r.user_id
) t ON t.user_id = u.id
SET u.wins   = COALESCE(t.wins, 0),
    u.losses = COALESCE(t.losses, 0),
    u.draws  = COALESCE(t.draws, 0);
