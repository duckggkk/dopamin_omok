package com.dopamin.omok.game.adapter.out.persistence;

import com.dopamin.omok.game.domain.GameType;
import com.dopamin.omok.game.domain.RoomStatus;

/**
 * 방 검색 조건. status 는 필수, gameType·ratingBand 는 선택(null 이면 미적용)이라
 * 하나의 동적 쿼리로 "상태별 / 상태+모드별 / 내 레이팅대 추천"을 모두 처리한다.
 */
public record RoomSearchCondition(
        RoomStatus status,
        GameType gameType,        // null 이면 모드 무관
        RatingBand ratingBand     // null 이면 '내 레이팅대 추천' 미적용
) {
    /** 방장 레이팅 추천 구간(모드별 하한/상한). */
    public record RatingBand(int classicMin, int classicMax, int physicalMin, int physicalMax) {}

    public static RoomSearchCondition of(RoomStatus status) {
        return new RoomSearchCondition(status, null, null);
    }

    public static RoomSearchCondition of(RoomStatus status, GameType gameType) {
        return new RoomSearchCondition(status, gameType, null);
    }

    public static RoomSearchCondition recommended(RoomStatus status, RatingBand ratingBand) {
        return new RoomSearchCondition(status, null, ratingBand);
    }
}
