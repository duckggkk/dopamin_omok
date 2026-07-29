package com.dopamin.omok.user.adapter.out.persistence;

import com.dopamin.omok.game.domain.GameStatus;
import com.dopamin.omok.game.domain.GameType;
import com.dopamin.omok.game.domain.QGame;
import com.dopamin.omok.user.application.dto.ModeStats;
import com.dopamin.omok.user.application.port.out.LoadUserGameStatsPort;
import com.dopamin.omok.user.domain.UserRole;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.EnumPath;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 끝난 대국(games) + 방의 모드(room.gameType)로 사용자의 모드별 전적을 QueryDSL 로 집계한다.
 * (친구 상대전적 HeadToHeadPersistenceAdapter 와 동일한 패턴 — 별도 컬럼/마이그레이션 없이 과거 기록도 정확)
 *
 * 분류:
 *  - 승: winner = 나
 *  - 패: status=FINISHED 이고 winner ≠ 나 (참가자 한정)
 *  - 무: status=DRAW
 *
 * 승/패/무를 각각 세면 사용자 1명당 쿼리 3번이 나가 랭킹(최대 100명)에서 N+1 이 된다.
 * 그래서 CASE WHEN 합계로 한 번에 세고, 사용자도 IN + GROUP BY 로 묶어서 조회한다.
 * 내가 흑이었는지 백이었는지에 따라 묶는 기준 컬럼이 달라지므로 흑/백 두 번만 돌린다.
 */
@Component
@RequiredArgsConstructor
public class UserGameStatsPersistenceAdapter implements LoadUserGameStatsPort {

    private final JPAQueryFactory queryFactory;

    @Override
    public ModeStats statsByMode(Long userId, GameType mode) {
        return statsByModeForUsers(List.of(userId), mode)
                .getOrDefault(userId, ModeStats.of(0, 0, 0));
    }

    @Override
    public Map<Long, ModeStats> statsByModeForUsers(Collection<Long> userIds, GameType mode) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        QGame g = QGame.game;
        Map<Long, ModeStats> result = new HashMap<>();
        // 같은 사람이 흑으로도 백으로도 뒀으면 양쪽 결과가 나오므로 아래에서 합산한다.
        accumulate(result, g.blackPlayer.id, userIds, mode);
        accumulate(result, g.whitePlayer.id, userIds, mode);
        return result;
    }

    /** {@code sideId}(흑 또는 백 자리) 가 조회 대상인 대국만 모아 그 자리 기준으로 승/패/무를 센다. */
    private void accumulate(Map<Long, ModeStats> result, NumberPath<Long> sideId,
                            Collection<Long> userIds, GameType mode) {
        QGame g = QGame.game;
        // 방의 랭크/캐주얼 표시는 보지 않는다 — 회원 대 회원 대국이면 모두 전적에 들어간다.
        // 제외 대상은 Game.isRated() 의 레이팅 반영 규칙과 같게 유지한다(봇 연습·게스트 대국).
        BooleanExpression base = g.room.gameType.eq(mode)
                .and(sideId.in(userIds))
                .and(ratedPlayer(g.blackPlayer.role))
                .and(ratedPlayer(g.whitePlayer.role));

        NumberExpression<Integer> wins = countWhen(g.winner.id.eq(sideId));
        NumberExpression<Integer> losses = countWhen(
                g.status.eq(GameStatus.FINISHED).and(g.winner.id.ne(sideId)));
        NumberExpression<Integer> draws = countWhen(g.status.eq(GameStatus.DRAW));

        List<Tuple> rows = queryFactory
                .select(sideId, wins, losses, draws)
                .from(g)
                .where(base)
                .groupBy(sideId)
                .fetch();

        for (Tuple row : rows) {
            Long id = row.get(sideId);
            if (id == null) {
                continue;
            }
            ModeStats stats = ModeStats.of(value(row.get(wins)), value(row.get(losses)), value(row.get(draws)));
            // totalGames·winRate 는 ModeStats.of 가 다시 계산하므로 승/패/무만 더하면 된다.
            result.merge(id, stats, (left, right) -> ModeStats.of(
                    left.wins() + right.wins(),
                    left.losses() + right.losses(),
                    left.draws() + right.draws()));
        }
    }

    private NumberExpression<Integer> countWhen(BooleanExpression condition) {
        return new CaseBuilder().when(condition).then(1).otherwise(0).sum();
    }

    private BooleanExpression ratedPlayer(EnumPath<UserRole> role) {
        return role.ne(UserRole.BOT).and(role.ne(UserRole.GUEST));
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }
}
