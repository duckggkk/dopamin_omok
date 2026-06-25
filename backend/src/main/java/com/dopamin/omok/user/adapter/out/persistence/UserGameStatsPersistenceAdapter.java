package com.dopamin.omok.user.adapter.out.persistence;

import com.dopamin.omok.game.domain.GameStatus;
import com.dopamin.omok.game.domain.GameType;
import com.dopamin.omok.game.domain.QGame;
import com.dopamin.omok.user.application.dto.ModeStats;
import com.dopamin.omok.user.application.port.out.LoadUserGameStatsPort;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 끝난 대국(games) + 방의 모드(room.gameType)로 사용자의 모드별 전적을 QueryDSL 로 집계한다.
 * (친구 상대전적 HeadToHeadPersistenceAdapter 와 동일한 패턴 — 별도 컬럼/마이그레이션 없이 과거 기록도 정확)
 *
 * 분류:
 *  - 승: winner = 나
 *  - 패: status=FINISHED 이고 winner ≠ 나 (참가자 한정)
 *  - 무: status=DRAW
 */
@Component
@RequiredArgsConstructor
public class UserGameStatsPersistenceAdapter implements LoadUserGameStatsPort {

    private final JPAQueryFactory queryFactory;

    @Override
    public ModeStats statsByMode(Long userId, GameType mode) {
        QGame g = QGame.game;
        // 랭크전(room.ranked=true)만 집계 — 전적/랭킹은 랭크 기록이다. 캐주얼은 casualStats 로 분리.
        BooleanExpression base = g.room.gameType.eq(mode)
                .and(g.room.ranked.isTrue())
                .and(g.blackPlayer.id.eq(userId).or(g.whitePlayer.id.eq(userId)));
        return aggregate(userId, base);
    }

    @Override
    public ModeStats casualStats(Long userId) {
        QGame g = QGame.game;
        // 캐주얼(room.ranked=false) 전적 — 모드(일반/피지컬) 구분 없이 합산(캐주얼은 레이팅이 없음).
        BooleanExpression base = g.room.ranked.isFalse()
                .and(g.blackPlayer.id.eq(userId).or(g.whitePlayer.id.eq(userId)));
        return aggregate(userId, base);
    }

    private ModeStats aggregate(Long userId, BooleanExpression base) {
        QGame g = QGame.game;
        int wins = count(base.and(g.winner.id.eq(userId)));
        int losses = count(base.and(g.status.eq(GameStatus.FINISHED)).and(g.winner.id.ne(userId)));
        int draws = count(base.and(g.status.eq(GameStatus.DRAW)));
        return ModeStats.of(wins, losses, draws);
    }

    private int count(BooleanExpression where) {
        QGame g = QGame.game;
        Long c = queryFactory.select(g.count()).from(g).where(where).fetchOne();
        return c == null ? 0 : c.intValue();
    }
}
