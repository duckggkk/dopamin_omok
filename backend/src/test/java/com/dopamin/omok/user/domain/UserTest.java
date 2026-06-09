package com.dopamin.omok.user.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @Test
    @DisplayName("로컬 가입 사용자의 기본값")
    void createLocalUserDefaults() {
        User user = User.createLocalUser("a@b.com", "encoded", "닉네임");

        assertThat(user.getEmail()).isEqualTo("a@b.com");
        assertThat(user.getNickname()).isEqualTo("닉네임");
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.getCurrency()).isZero();
        assertThat(user.getTokenVersion()).isZero();
        assertThat(user.getPublicId()).isNotNull();
    }

    @Test
    @DisplayName("tokenVersion 증가")
    void incrementTokenVersion() {
        User user = User.createLocalUser("a@b.com", "p", "n");
        user.incrementTokenVersion();
        assertThat(user.getTokenVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("재화 충전/사용")
    void chargeAndSpendCurrency() {
        User user = User.createLocalUser("a@b.com", "p", "n");
        user.chargeCurrency(100);
        assertThat(user.getCurrency()).isEqualTo(100);

        user.spendCurrency(30);
        assertThat(user.getCurrency()).isEqualTo(70);
    }

    @Test
    @DisplayName("잔액 부족 시 사용 불가")
    void spendCurrencyInsufficient() {
        User user = User.createLocalUser("a@b.com", "p", "n");
        user.chargeCurrency(10);
        assertThatThrownBy(() -> user.spendCurrency(20))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("전적 기록과 총 게임 수")
    void recordStats() {
        User user = User.createLocalUser("a@b.com", "p", "n");
        user.recordWin();
        user.recordWin();
        user.recordLoss();
        user.recordDraw();

        assertThat(user.getWins()).isEqualTo(2);
        assertThat(user.getLosses()).isEqualTo(1);
        assertThat(user.getDraws()).isEqualTo(1);
        assertThat(user.getTotalGames()).isEqualTo(4);
    }
}
