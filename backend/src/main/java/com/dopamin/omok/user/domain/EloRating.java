package com.dopamin.omok.user.domain;

/**
 * ELO 레이팅 계산기. 두 유저의 현재 레이팅을 기준으로 결과(승/패 또는 무)를 반영해
 * 각 유저의 레이팅을 제로섬으로 가감한다. (승자가 얻는 만큼 패자가 잃는다.)
 *
 * <p>기대 승률 E = 1 / (1 + 10^((상대-나)/400)), 변동폭 = K * (실제점수 - 기대 승률).
 * 실제점수는 승=1, 무=0.5, 패=0.</p>
 */
public final class EloRating {

    public static final int DEFAULT_RATING = 1000;
    public static final int MIN_RATING = 100;
    private static final int K = 32;

    private EloRating() {
    }

    /** 승패가 갈린 결과를 해당 모드(physical 여부) 레이팅에 반영한다(제로섬). */
    public static void applyResult(User winner, User loser, boolean physical) {
        int winnerDelta = delta(winner.getRating(physical), loser.getRating(physical), 1.0);
        winner.adjustRating(physical, winnerDelta);
        loser.adjustRating(physical, -winnerDelta);
    }

    /** 무승부를 해당 모드(physical 여부) 레이팅에 반영한다. */
    public static void applyDraw(User a, User b, boolean physical) {
        int aDelta = delta(a.getRating(physical), b.getRating(physical), 0.5);
        a.adjustRating(physical, aDelta);
        b.adjustRating(physical, -aDelta);
    }

    private static int delta(int self, int opponent, double actualScore) {
        double expected = 1.0 / (1.0 + Math.pow(10, (opponent - self) / 400.0));
        return (int) Math.round(K * (actualScore - expected));
    }
}
