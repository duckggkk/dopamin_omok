package com.dopamin.omok.user.domain;

import com.dopamin.omok.game.domain.GameType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EloRating 은 static 메서드뿐이라 스프링 컨텍스트 없이 검증할 수 있다.
 * <p>
 * 레이팅은 틀려도 예외가 나지 않고 잘못된 숫자가 조용히 저장되는 영역이다.
 * 특히 "어느 레이팅(일반/피지컬)에 반영하는가"는 컴파일러가 잡아주지 못하므로
 * 리팩토링 전에 기대값을 고정해 둔다.
 */
class EloRatingTest {

    /** 일반·피지컬 레이팅을 각각 원하는 값으로 맞춘 유저를 만든다(기본 1000 에서 delta 로 이동). */
    private static User userAt(String name, int classic, int physical) {
        User user = User.createLocalUser(name + "@test.local", "pw", name);
        user.adjustRating(GameType.CLASSIC, classic - EloRating.DEFAULT_RATING);
        user.adjustRating(GameType.PHYSICAL, physical - EloRating.DEFAULT_RATING);
        return user;
    }

    /** 두 모드 레이팅이 같은 유저. */
    private static User userAt(String name, int rating) {
        return userAt(name, rating, rating);
    }

    @Test
    @DisplayName("레이팅이 같으면 승자가 16점 얻고 패자가 16점 잃는다 (K=32, 기대승률 0.5)")
    void sameRatingExchangesHalfK() {
        User winner = userAt("w", 1000);
        User loser = userAt("l", 1000);

        EloRating.applyResult(winner, loser, GameType.CLASSIC);

        // delta = round(32 * (1 - 0.5)) = 16
        assertThat(winner.getClassicRating()).isEqualTo(1016);
        assertThat(loser.getClassicRating()).isEqualTo(984);
    }

    @Test
    @DisplayName("제로섬 — 승자가 얻은 만큼 패자가 잃으므로 두 레이팅의 합은 변하지 않는다")
    void isZeroSum() {
        User winner = userAt("w", 1300);
        User loser = userAt("l", 900);
        int sumBefore = winner.getClassicRating() + loser.getClassicRating();

        EloRating.applyResult(winner, loser, GameType.CLASSIC);

        assertThat(winner.getClassicRating() + loser.getClassicRating()).isEqualTo(sumBefore);
    }

    @Test
    @DisplayName("강한 상대를 이기면 많이 오르고, 약한 상대를 이기면 조금 오른다")
    void gainDependsOnOpponentStrength() {
        // 약한 상대(800)를 이긴 강자(1200): 기대승률 0.909 → delta 3
        User favorite = userAt("favorite", 1200);
        User underdog = userAt("underdog", 800);
        EloRating.applyResult(favorite, underdog, GameType.CLASSIC);
        assertThat(favorite.getClassicRating()).isEqualTo(1203);
        assertThat(underdog.getClassicRating()).isEqualTo(797);

        // 강한 상대(1200)를 이긴 약자(800): 기대승률 0.091 → delta 29
        User winner = userAt("winner", 800);
        User loser = userAt("loser", 1200);
        EloRating.applyResult(winner, loser, GameType.CLASSIC);
        assertThat(winner.getClassicRating()).isEqualTo(829);
        assertThat(loser.getClassicRating()).isEqualTo(1171);
    }

    @Test
    @DisplayName("CLASSIC 이면 일반 레이팅만 바뀌고 피지컬 레이팅은 그대로다")
    void classicModeTouchesOnlyClassicRating() {
        User winner = userAt("w", 1000, 1500);
        User loser = userAt("l", 1000, 1500);

        EloRating.applyResult(winner, loser, GameType.CLASSIC);

        assertThat(winner.getClassicRating()).isEqualTo(1016);
        assertThat(loser.getClassicRating()).isEqualTo(984);
        assertThat(winner.getPhysicalRating()).isEqualTo(1500);   // 손대지 않는다
        assertThat(loser.getPhysicalRating()).isEqualTo(1500);
    }

    @Test
    @DisplayName("PHYSICAL 이면 피지컬 레이팅만 바뀌고 일반 레이팅은 그대로다")
    void physicalModeTouchesOnlyPhysicalRating() {
        User winner = userAt("w", 1500, 1000);
        User loser = userAt("l", 1500, 1000);

        EloRating.applyResult(winner, loser, GameType.PHYSICAL);

        assertThat(winner.getPhysicalRating()).isEqualTo(1016);
        assertThat(loser.getPhysicalRating()).isEqualTo(984);
        assertThat(winner.getClassicRating()).isEqualTo(1500);    // 손대지 않는다
        assertThat(loser.getClassicRating()).isEqualTo(1500);
    }

    @Test
    @DisplayName("하한(100) 미만으로는 내려가지 않는다 — 이때는 제로섬이 깨진다")
    void neverDropsBelowMinRating() {
        User winner = userAt("w", EloRating.MIN_RATING);
        User loser = userAt("l", EloRating.MIN_RATING);

        EloRating.applyResult(winner, loser, GameType.CLASSIC);

        assertThat(winner.getClassicRating()).isEqualTo(EloRating.MIN_RATING + 16);
        assertThat(loser.getClassicRating()).isEqualTo(EloRating.MIN_RATING);  // 84 로 내려가지 않는다
    }

    @Test
    @DisplayName("무승부: 레이팅이 같으면 아무 변화가 없다")
    void drawBetweenEqualsChangesNothing() {
        User a = userAt("a", 1000);
        User b = userAt("b", 1000);

        EloRating.applyDraw(a, b, GameType.CLASSIC);

        // delta = round(32 * (0.5 - 0.5)) = 0
        assertThat(a.getClassicRating()).isEqualTo(1000);
        assertThat(b.getClassicRating()).isEqualTo(1000);
    }

    @Test
    @DisplayName("무승부: 레이팅이 높은 쪽이 잃고 낮은 쪽이 얻는다")
    void drawFavorsTheUnderdog() {
        User favorite = userAt("favorite", 1200);
        User underdog = userAt("underdog", 800);

        EloRating.applyDraw(favorite, underdog, GameType.CLASSIC);

        // 강자 기대승률 0.909 → delta = round(32 * (0.5 - 0.909)) = -13
        assertThat(favorite.getClassicRating()).isEqualTo(1187);
        assertThat(underdog.getClassicRating()).isEqualTo(813);
    }

    @Test
    @DisplayName("무승부도 종류를 구분한다 — PHYSICAL 이면 일반 레이팅은 그대로다")
    void drawRespectsMode() {
        User a = userAt("a", 1500, 1200);
        User b = userAt("b", 1500, 800);

        EloRating.applyDraw(a, b, GameType.PHYSICAL);

        assertThat(a.getPhysicalRating()).isEqualTo(1187);
        assertThat(b.getPhysicalRating()).isEqualTo(813);
        assertThat(a.getClassicRating()).isEqualTo(1500);
        assertThat(b.getClassicRating()).isEqualTo(1500);
    }
}
