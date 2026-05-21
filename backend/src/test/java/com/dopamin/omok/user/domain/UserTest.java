package com.dopamin.omok.user.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    @DisplayName("로컬 유저 생성 시 기본값이 설정됨")
    void createLocalUser_setsDefaults() {
        User user = User.createLocalUser("test@test.com", "encoded", "nick");

        assertThat(user.getEmail()).isEqualTo("test@test.com");
        assertThat(user.getNickname()).isEqualTo("nick");
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(user.getWins()).isZero();
        assertThat(user.getLosses()).isZero();
        assertThat(user.getDraws()).isZero();
        assertThat(user.getPublicId()).isNotNull();
    }

    @Test
    @DisplayName("소셜 유저 생성 시 provider 정보가 설정됨")
    void createSocialUser_setsProvider() {
        User user = User.createSocialUser("social@test.com", "socialNick",
                AuthProvider.GOOGLE, "google-id-123", "http://img.url");

        assertThat(user.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(user.getProviderId()).isEqualTo("google-id-123");
        assertThat(user.getProfileImageUrl()).isEqualTo("http://img.url");
        assertThat(user.getPassword()).isNull();
        assertThat(user.getPublicId()).isNotNull();
    }

    @Test
    @DisplayName("서로 다른 유저의 publicId는 고유함")
    void createUser_publicIdIsUnique() {
        User user1 = User.createLocalUser("a@test.com", "pass", "nick1");
        User user2 = User.createLocalUser("b@test.com", "pass", "nick2");

        assertThat(user1.getPublicId()).isNotEqualTo(user2.getPublicId());
    }

    @Test
    @DisplayName("닉네임 변경")
    void updateNickname() {
        User user = User.createLocalUser("test@test.com", "pass", "oldNick");
        user.updateNickname("newNick");

        assertThat(user.getNickname()).isEqualTo("newNick");
    }

    @Test
    @DisplayName("프로필 이미지 변경")
    void updateProfileImageUrl() {
        User user = User.createLocalUser("test@test.com", "pass", "nick");
        user.updateProfileImageUrl("http://new.img/url");

        assertThat(user.getProfileImageUrl()).isEqualTo("http://new.img/url");
    }

    @Test
    @DisplayName("승패무 기록이 정확히 반영됨")
    void recordStats() {
        User user = User.createLocalUser("test@test.com", "pass", "nick");

        user.recordWin();
        user.recordWin();
        user.recordLoss();
        user.recordDraw();

        assertThat(user.getWins()).isEqualTo(2);
        assertThat(user.getLosses()).isEqualTo(1);
        assertThat(user.getDraws()).isEqualTo(1);
    }

    @Test
    @DisplayName("총 게임 수는 승+패+무 합산")
    void getTotalGames() {
        User user = User.createLocalUser("test@test.com", "pass", "nick");

        user.recordWin();
        user.recordLoss();
        user.recordDraw();

        assertThat(user.getTotalGames()).isEqualTo(3);
    }
}
