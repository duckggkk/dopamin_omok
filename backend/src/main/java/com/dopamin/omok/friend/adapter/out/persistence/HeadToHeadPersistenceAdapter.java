package com.dopamin.omok.friend.adapter.out.persistence;

import com.dopamin.omok.friend.application.dto.HeadToHead;
import com.dopamin.omok.friend.application.port.out.LoadHeadToHeadPort;
import com.dopamin.omok.game.domain.GameStatus;
import com.dopamin.omok.game.domain.QGame;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
        QGame g = QGame.game;
        BooleanExpression pair = g.blackPlayer.id.eq(meId).and(g.whitePlayer.id.eq(otherId))
                .or(g.blackPlayer.id.eq(otherId).and(g.whitePlayer.id.eq(meId)));

        int wins = count(pair.and(g.winner.id.eq(meId)));
        int losses = count(pair.and(g.winner.id.eq(otherId)));
        int draws = count(pair.and(g.status.eq(GameStatus.DRAW)));
        return new HeadToHead(wins, losses, draws);
    }

    private int count(BooleanExpression where) {
        QGame g = QGame.game;
        Long c = queryFactory.select(g.count()).from(g).where(where).fetchOne();
        return c == null ? 0 : c.intValue();
    }
}
