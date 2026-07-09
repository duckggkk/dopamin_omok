package com.dopamin.omok.friend.adapter.out.persistence;

import com.dopamin.omok.friend.application.dto.HeadToHead;
import com.dopamin.omok.friend.application.port.out.LoadHeadToHeadPort;
import com.dopamin.omok.game.domain.GameStatus;
import com.dopamin.omok.game.domain.QGame;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 끝난 대국(games)에서 두 사용자의 상대 전적을 QueryDSL 로 집계한다.
 * 승/패는 winner 기준(FINISHED·기권 포함), 무는 status=DRAW 기준.
 */
@Component
@RequiredArgsConstructor
public class HeadToHeadPersistenceAdapter implements LoadHeadToHeadPort {

    private final JPAQueryFactory queryFactory;

    @Override
    public HeadToHead between(Long meId, Long otherId) {
        return betweenMany(meId, List.of(otherId)).getOrDefault(otherId, HeadToHead.empty());
    }

    @Override
    public Map<Long, HeadToHead> betweenMany(Long meId, Collection<Long> otherIds) {
        if (otherIds == null || otherIds.isEmpty()) {
            return Map.of();
        }

        QGame g = QGame.game;
        BooleanExpression pair = g.blackPlayer.id.eq(meId).and(g.whitePlayer.id.in(otherIds))
                .or(g.whitePlayer.id.eq(meId).and(g.blackPlayer.id.in(otherIds)));

        NumberExpression<Long> opponentId = new CaseBuilder()
                .when(g.blackPlayer.id.eq(meId)).then(g.whitePlayer.id)
                .otherwise(g.blackPlayer.id);
        NumberExpression<Integer> wins = new CaseBuilder()
                .when(g.winner.id.eq(meId)).then(1)
                .otherwise(0)
                .sum();
        NumberExpression<Integer> losses = new CaseBuilder()
                .when(g.winner.id.isNotNull().and(g.winner.id.ne(meId))).then(1)
                .otherwise(0)
                .sum();
        NumberExpression<Integer> draws = new CaseBuilder()
                .when(g.status.eq(GameStatus.DRAW)).then(1)
                .otherwise(0)
                .sum();

        List<Tuple> rows = queryFactory
                .select(opponentId, wins, losses, draws)
                .from(g)
                .where(pair)
                .groupBy(g.blackPlayer.id, g.whitePlayer.id)
                .fetch();

        Map<Long, HeadToHead> result = new HashMap<>();
        for (Tuple row : rows) {
            Long id = row.get(opponentId);
            if (id != null) {
                HeadToHead head = new HeadToHead(
                        value(row.get(wins)),
                        value(row.get(losses)),
                        value(row.get(draws)));
                result.merge(id, head, (left, right) -> new HeadToHead(
                        left.wins() + right.wins(),
                        left.losses() + right.losses(),
                        left.draws() + right.draws()));
            }
        }
        return result;
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }
}
