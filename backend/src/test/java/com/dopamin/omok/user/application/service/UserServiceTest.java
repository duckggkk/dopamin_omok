package com.dopamin.omok.user.application.service;

import com.dopamin.omok.global.common.exception.ErrorCode;
import com.dopamin.omok.global.common.exception.OmokException;
import com.dopamin.omok.support.UserFixture;
import com.dopamin.omok.user.application.dto.UserResponse;
import com.dopamin.omok.user.application.port.out.CheckUserExistsPort;
import com.dopamin.omok.user.application.port.out.LoadUserPort;
import com.dopamin.omok.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock LoadUserPort loadUserPort;
    @Mock CheckUserExistsPort checkUserExistsPort;
    @InjectMocks UserService userService;

    // ── getUser ─────────────────────────────────────────────────

    @Test
    @DisplayName("ID로 유저 조회 성공")
    void getUser_success() {
        User user = UserFixture.create(1L);
        given(loadUserPort.findById(1L)).willReturn(Optional.of(user));

        UserResponse result = userService.getUser(1L);

        assertThat(result.id()).isEqualTo(user.getPublicId());
        assertThat(result.email()).isEqualTo(user.getEmail());
        assertThat(result.nickname()).isEqualTo(user.getNickname());
    }

    @Test
    @DisplayName("존재하지 않는 ID로 조회 시 예외")
    void getUser_notFound_throwsException() {
        given(loadUserPort.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser(99L))
                .isInstanceOf(OmokException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // ── getUserByPublicId ────────────────────────────────────────

    @Test
    @DisplayName("publicId로 유저 조회 성공")
    void getUserByPublicId_success() {
        User user = UserFixture.create(1L);
        UUID publicId = user.getPublicId();
        given(loadUserPort.findByPublicId(publicId)).willReturn(Optional.of(user));

        UserResponse result = userService.getUserByPublicId(publicId);

        assertThat(result.id()).isEqualTo(publicId);
    }

    @Test
    @DisplayName("존재하지 않는 publicId 조회 시 예외")
    void getUserByPublicId_notFound_throwsException() {
        UUID randomId = UUID.randomUUID();
        given(loadUserPort.findByPublicId(randomId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserByPublicId(randomId))
                .isInstanceOf(OmokException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // ── updateProfile ────────────────────────────────────────────

    @Test
    @DisplayName("닉네임 변경 성공")
    void updateProfile_changeNickname_success() {
        User user = UserFixture.create(1L, "test@test.com", "oldNick");
        given(loadUserPort.findById(1L)).willReturn(Optional.of(user));
        given(checkUserExistsPort.existsByNickname("newNick")).willReturn(false);

        UserResponse result = userService.updateProfile(1L, "newNick", null);

        assertThat(result.nickname()).isEqualTo("newNick");
    }

    @Test
    @DisplayName("중복 닉네임으로 변경 시 예외")
    void updateProfile_duplicateNickname_throwsException() {
        User user = UserFixture.create(1L, "test@test.com", "oldNick");
        given(loadUserPort.findById(1L)).willReturn(Optional.of(user));
        given(checkUserExistsPort.existsByNickname("taken")).willReturn(true);

        assertThatThrownBy(() -> userService.updateProfile(1L, "taken", null))
                .isInstanceOf(OmokException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NICKNAME_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("현재와 동일한 닉네임은 중복 체크 생략")
    void updateProfile_sameNickname_noValidation() {
        User user = UserFixture.create(1L, "test@test.com", "sameNick");
        given(loadUserPort.findById(1L)).willReturn(Optional.of(user));

        userService.updateProfile(1L, "sameNick", null);

        then(checkUserExistsPort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("프로필 이미지 URL 변경")
    void updateProfile_changeProfileImage() {
        User user = UserFixture.create(1L);
        given(loadUserPort.findById(1L)).willReturn(Optional.of(user));

        UserResponse result = userService.updateProfile(1L, null, "http://new.img/photo.png");

        assertThat(result.profileImageUrl()).isEqualTo("http://new.img/photo.png");
    }
}
