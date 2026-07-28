package com.dopamin.omok.user.application.service;

import com.dopamin.omok.auth.application.port.out.DeleteRefreshTokenPort;
import com.dopamin.omok.game.application.port.out.LoadGamePlayerPort;
import com.dopamin.omok.game.domain.GameType;
import com.dopamin.omok.global.common.exception.ErrorCode;
import com.dopamin.omok.global.common.exception.OmokException;
import com.dopamin.omok.user.application.port.out.LoadUserPort;
import com.dopamin.omok.user.application.port.out.SaveUserPort;
import com.dopamin.omok.user.domain.AuthProvider;
import com.dopamin.omok.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WithdrawServiceTest {

    private static final Long USER_ID = 42L;

    @Mock private LoadUserPort loadUserPort;
    @Mock private SaveUserPort saveUserPort;
    @Mock private LoadGamePlayerPort loadGamePlayerPort;
    @Mock private DeleteRefreshTokenPort deleteRefreshTokenPort;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private WithdrawService withdrawService;

    private User localUser() {
        User user = User.createLocalUser("me@example.com", "encoded-pw", "도파민왕");
        user.verifyEmail();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    private User googleUser() {
        User user = User.createSocialUser(
                "me@gmail.com", "구글유저", AuthProvider.GOOGLE, "google-123", "https://img/p.png");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    private User guestUser() {
        User user = User.createGuestUser("guest_x@guest.local", "게스트1234");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    @Test
    @DisplayName("비밀번호가 맞으면 계정이 익명화되고 refresh token 이 폐기된다")
    void withdrawAnonymizesAccount() {
        User user = localUser();
        when(loadUserPort.findById(USER_ID)).thenReturn(Optional.of(user));
        when(loadGamePlayerPort.existsInActiveRoom(USER_ID)).thenReturn(false);
        when(passwordEncoder.matches("my-password", "encoded-pw")).thenReturn(true);

        withdrawService.withdraw(USER_ID, "my-password");

        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getDeletedAt()).isNotNull();
        // 개인정보는 파기된다
        assertThat(user.getEmail()).isEqualTo("deleted_" + user.getPublicId() + "@deleted.local");
        assertThat(user.getPassword()).isNull();
        assertThat(user.getProfileImageUrl()).isNull();
        assertThat(user.getProviderId()).isNull();
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.isProfilePrivate()).isTrue();
        // 닉네임은 UNIQUE 라 지우지 못하고, id 로 유일한 익명 이름을 만든다
        assertThat(user.getNickname()).isEqualTo("탈퇴한사용자_" + USER_ID);

        verify(saveUserPort).save(user);
        verify(deleteRefreshTokenPort).deleteByUserId(USER_ID);
    }

    @Test
    @DisplayName("전적·레이팅은 남긴다 — 상대방 대국 기록의 무결성에 쓰이는 값이다")
    void withdrawKeepsGameRecords() {
        User user = localUser();
        user.recordWin();
        user.adjustRating(GameType.CLASSIC, 30);
        when(loadUserPort.findById(USER_ID)).thenReturn(Optional.of(user));
        when(loadGamePlayerPort.existsInActiveRoom(USER_ID)).thenReturn(false);
        when(passwordEncoder.matches(any(), any())).thenReturn(true);

        withdrawService.withdraw(USER_ID, "my-password");

        assertThat(user.getWins()).isEqualTo(1);
        assertThat(user.getClassicRating()).isEqualTo(1030);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 탈퇴되지 않는다")
    void withdrawRejectsWrongPassword() {
        User user = localUser();
        when(loadUserPort.findById(USER_ID)).thenReturn(Optional.of(user));
        when(loadGamePlayerPort.existsInActiveRoom(USER_ID)).thenReturn(false);
        when(passwordEncoder.matches("wrong", "encoded-pw")).thenReturn(false);

        assertThatThrownBy(() -> withdrawService.withdraw(USER_ID, "wrong"))
                .isInstanceOfSatisfying(OmokException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.WITHDRAW_PASSWORD_MISMATCH));

        assertThat(user.isDeleted()).isFalse();
        verify(saveUserPort, never()).save(any());
        verify(deleteRefreshTokenPort, never()).deleteByUserId(any());
    }

    @Test
    @DisplayName("비밀번호를 아예 보내지 않아도 거부한다(일반 가입 계정)")
    void withdrawRejectsMissingPassword() {
        User user = localUser();
        when(loadUserPort.findById(USER_ID)).thenReturn(Optional.of(user));
        when(loadGamePlayerPort.existsInActiveRoom(USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> withdrawService.withdraw(USER_ID, null))
                .isInstanceOfSatisfying(OmokException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.WITHDRAW_PASSWORD_MISMATCH));

        assertThat(user.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("소셜 전용 계정은 확인할 비밀번호가 없으므로 그대로 탈퇴된다")
    void withdrawSocialAccountWithoutPassword() {
        User user = googleUser();
        when(loadUserPort.findById(USER_ID)).thenReturn(Optional.of(user));
        when(loadGamePlayerPort.existsInActiveRoom(USER_ID)).thenReturn(false);

        withdrawService.withdraw(USER_ID, null);

        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getProviderId()).isNull();
        verify(deleteRefreshTokenPort).deleteByUserId(USER_ID);
    }

    @Test
    @DisplayName("진행 중인 방·대국이 있으면 막는다 — 상대가 멈춘 판에 갇히지 않도록")
    void withdrawBlockedWhileInActiveRoom() {
        User user = localUser();
        when(loadUserPort.findById(USER_ID)).thenReturn(Optional.of(user));
        when(loadGamePlayerPort.existsInActiveRoom(USER_ID)).thenReturn(true);

        assertThatThrownBy(() -> withdrawService.withdraw(USER_ID, "my-password"))
                .isInstanceOfSatisfying(OmokException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.WITHDRAW_IN_ACTIVE_GAME));

        assertThat(user.isDeleted()).isFalse();
        verify(saveUserPort, never()).save(any());
    }

    @Test
    @DisplayName("게스트 계정은 탈퇴 대상이 아니다")
    void withdrawRejectsGuest() {
        when(loadUserPort.findById(USER_ID)).thenReturn(Optional.of(guestUser()));

        assertThatThrownBy(() -> withdrawService.withdraw(USER_ID, null))
                .isInstanceOfSatisfying(OmokException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.WITHDRAW_GUEST_NOT_ALLOWED));

        verify(saveUserPort, never()).save(any());
    }

    @Test
    @DisplayName("이미 탈퇴한 계정은 조회 단계에서 걸러져 중복 탈퇴가 되지 않는다")
    void withdrawTwiceIsRejected() {
        // LoadUserPort 는 탈퇴 회원을 돌려주지 않으므로 두 번째 호출은 빈 Optional 이다.
        when(loadUserPort.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> withdrawService.withdraw(USER_ID, "my-password"))
                .isInstanceOfSatisfying(OmokException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));

        verify(deleteRefreshTokenPort, never()).deleteByUserId(any());
    }
}
