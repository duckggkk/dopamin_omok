package com.dopamin.omok.game.adapter.out.persistence;

import com.dopamin.omok.game.domain.GameType;
import com.dopamin.omok.game.domain.QGamePlayer;
import com.dopamin.omok.game.domain.QRoom;
import com.dopamin.omok.game.domain.Room;
import com.dopamin.omok.user.domain.UserRole;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 방 검색 동적 쿼리(QueryDSL). 과거 JPQL 3종(상태 / 상태+모드 / 레이팅대 추천)을
 * 조건 조합 하나로 통합한다. 스프링 데이터가 RoomJpaRepository 의 프래그먼트 구현(...Impl)으로 자동 연결.
 */
@RequiredArgsConstructor
public class RoomRepositoryCustomImpl implements RoomRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Room> search(RoomSearchCondition condition, Pageable pageable) {
        QRoom room = QRoom.room;

        BooleanBuilder where = new BooleanBuilder();
        where.and(room.status.eq(condition.status()));
        // AI 연습 방(봇이 참가한 방)은 로비 목록(대기/관전/추천)에 노출하지 않는다.
        // 연습을 시작한 본인은 생성 응답의 방 코드로 바로 입장하므로 목록에 뜰 필요가 없다.
        QGamePlayer gp = QGamePlayer.gamePlayer;
        where.and(JPAExpressions.selectOne()
                .from(gp)
                .where(gp.room.eq(room), gp.user.role.eq(UserRole.BOT))
                .notExists());
        if (condition.gameType() != null) {
            where.and(room.gameType.eq(condition.gameType()));
        }
        if (condition.ranked() != null) {
            where.and(room.ranked.eq(condition.ranked()));
        }
        if (condition.ratingBand() != null) {
            RoomSearchCondition.RatingBand b = condition.ratingBand();
            // 일반 방은 방장 일반레이팅이, 피지컬 방은 방장 피지컬레이팅이 각 구간에 들면 추천
            where.and(
                    room.gameType.eq(GameType.CLASSIC)
                            .and(room.host.classicRating.between(b.classicMin(), b.classicMax()))
                    .or(room.gameType.eq(GameType.PHYSICAL)
                            .and(room.host.physicalRating.between(b.physicalMin(), b.physicalMax())))
            );
        }

        List<Room> content = queryFactory
                .selectFrom(room)
                .where(where)
                .orderBy(room.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(room.count())
                .from(room)
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0L : total);
    }
}
