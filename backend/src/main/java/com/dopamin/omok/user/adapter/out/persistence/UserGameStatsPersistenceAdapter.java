package com.dopamin.omok.user.adapter.out.persistence;

import com.dopamin.omok.game.domain.GameStatus;
import com.dopamin.omok.game.domain.GameType;
import com.dopamin.omok.game.domain.QGame;
import com.dopamin.omok.user.application.dto.ModeStats;
import com.dopamin.omok.user.application.port.out.LoadUserGameStatsPort;
import com.dopamin.omok.user.domain.UserRole;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.EnumPath;
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
        // 방의 랭크/캐주얼 표시는 보지 않는다 — 회원 대 회원 대국이면 모두 전적에 들어간다.
        // 제외 대상은 Game.isRated() 의 레이팅 반영 규칙과 같게 유지한다(봇 연습·게스트 대국).
        BooleanExpression base = g.room.gameType.eq(mode)
                .and(g.blackPlayer.id.eq(userId).or(g.whitePlayer.id.eq(userId)))
                .and(ratedPlayer(g.blackPlayer.role))
                .and(ratedPlayer(g.whitePlayer.role));
        return aggregate(userId, base);
    }

    private BooleanExpression ratedPlayer(EnumPath<UserRole> role) {
        return role.ne(UserRole.BOT).and(role.ne(UserRole.GUEST));
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
