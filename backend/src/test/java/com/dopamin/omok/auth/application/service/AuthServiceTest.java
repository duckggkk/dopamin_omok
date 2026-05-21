package com.dopamin.omok.auth.application.service;

import com.dopamin.omok.auth.application.dto.TokenResponse;
import com.dopamin.omok.auth.application.port.out.DeleteRefreshTokenPort;
import com.dopamin.omok.auth.application.port.out.LoadRefreshTokenPort;
import com.dopamin.omok.auth.application.port.out.SaveRefreshTokenPort;
import com.dopamin.omok.auth.domain.RefreshToken;
import com.dopamin.omok.global.common.exception.ErrorCode;
import com.dopamin.omok.global.common.exception.OmokException;
import com.dopamin.omok.global.security.jwt.JwtProvider;
import com.dopamin.omok.support.UserFixture;
import com.dopamin.omok.user.application.port.out.CheckUserExistsPort;
import com.dopamin.omok.user.application.port.out.LoadUserPort;
import com.dopamin.omok.user.application.port.out.SaveUserPort;
import com.dopamin.omok.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock LoadUserPort loadUserPort;
    @Mock SaveUserPort saveUserPort;
    @Mock CheckUserExistsPort checkUserExistsPort;
    @Mock LoadRefreshTokenPort loadRefreshTokenPort;
    @Mock SaveRefreshTokenPort saveRefreshTokenPort;
    @Mock DeleteRefreshTokenPort deleteRefreshTokenPort;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtProvider jwtProvider;
    @InjectMocks AuthService authService;

    // ── register ────────────────────────────────────────────────

    @Test
    @DisplayName("회원가입 성공 시 토큰 반환")
    void register_success_returnsTokens() {
        given(checkUserExistsPort.existsByEmail("new@test.com")).willReturn(false);
        given(checkUserExistsPort.existsByNickname("newNick")).willReturn(false);
        given(passwordEncoder.encode(anyString())).willReturn("encoded");
        given(saveUserPort.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
        given(jwtProvider.generateAccessToken(any(), anyString(), anyString())).willReturn("access-token");
        given(jwtProvider.generateRefreshToken(any())).willReturn("refresh-token");
        given(jwtProvider.getRefreshTokenExpirationMillis()).willReturn(604_800_000L);
        given(saveRefreshTokenPort.save(any())).willAnswer(inv -> inv.getArgument(0));

        TokenResponse result = authService.register("new@test.com", "Password1!", "newNick");

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.tokenType()).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("이메일 중복 시 예외")
    void register_duplicateEmail_throwsException() {
        given(checkUserExistsPort.existsByEmail("dup@test.com")).willReturn(true);

        assertThatThrownBy(() -> authService.register("dup@test.com", "pass", "nick"))
                .isInstanceOf(OmokException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("닉네임 중복 시 예외")
    void register_duplicateNickname_throwsException() {
        given(checkUserExistsPort.existsByEmail("new@test.com")).willReturn(false);
        given(checkUserExistsPort.existsByNickname("dupNick")).willReturn(true);

        assertThatThrownBy(() -> authService.register("new@test.com", "pass", "dupNick"))
                .isInstanceOf(OmokException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NICKNAME_ALREADY_EXISTS);
    }

    // ── login ────────────────────────────────────────────────────

    @Test
    @DisplayName("로그인 성공 시 토큰 반환")
    void login_success_returnsTokens() {
        User user = UserFixture.create(1L, "user@test.com", "nick");
        given(loadUserPort.findByEmail("user@test.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("rawPass", user.getPassword())).willReturn(true);
        given(jwtProvider.generateAccessToken(any(), anyString(), anyString())).willReturn("access");
        given(jwtProvider.generateRefreshToken(any())).willReturn("refresh");
        given(jwtProvider.getRefreshTokenExpirationMillis()).willReturn(604_800_000L);
        given(saveRefreshTokenPort.save(any())).willAnswer(inv -> inv.getArgument(0));

        TokenResponse result = authService.login("user@test.com", "rawPass");

        assertThat(result.accessToken()).isEqualTo("access");
    }

    @Test
    @DisplayName("존재하지 않는 이메일로 로그인 시 예외")
    void login_emailNotFound_throwsException() {
        given(loadUserPort.findByEmail("ghost@test.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("ghost@test.com", "pass"))
                .isInstanceOf(OmokException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("비밀번호 불일치 시 예외")
    void login_wrongPassword_throwsException() {
        User user = UserFixture.create(1L, "user@test.com", "nick");
        given(loadUserPort.findByEmail("user@test.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrongPass", user.getPassword())).willReturn(false);

        assertThatThrownBy(() -> authService.login("user@test.com", "wrongPass"))
                .isInstanceOf(OmokException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    // ── refresh ──────────────────────────────────────────────────

    @Test
    @DisplayName("유효한 리프레시 토큰으로 재발급 성공")
    void refresh_success_returnsNewTokens() {
        User user = UserFixture.create(1L);
        RefreshToken token = RefreshToken.of(1L, "valid-token", LocalDateTime.now().plusDays(7));
        given(jwtProvider.validateToken("valid-token")).willReturn(true);
        given(loadRefreshTokenPort.findByToken("valid-token")).willReturn(Optional.of(token));
        given(loadUserPort.findById(1L)).willReturn(Optional.of(user));
        given(jwtProvider.generateAccessToken(any(), anyString(), anyString())).willReturn("new-access");
        given(jwtProvider.generateRefreshToken(any())).willReturn("new-refresh");
        given(jwtProvider.getRefreshTokenExpirationMillis()).willReturn(604_800_000L);
        given(saveRefreshTokenPort.save(any())).willAnswer(inv -> inv.getArgument(0));

        TokenResponse result = authService.refresh("valid-token");

        assertThat(result.accessToken()).isEqualTo("new-access");
        then(deleteRefreshTokenPort).should().delete(token);
    }

    @Test
    @DisplayName("유효하지 않은 리프레시 토큰 시 예외")
    void refresh_invalidToken_throwsException() {
        given(jwtProvider.validateToken("bad-token")).willReturn(false);

        assertThatThrownBy(() -> authService.refresh("bad-token"))
                .isInstanceOf(OmokException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("만료된 리프레시 토큰 시 예외 및 토큰 삭제")
    void refresh_expiredToken_deletesAndThrowsException() {
        RefreshToken expired = RefreshToken.of(1L, "expired-token", LocalDateTime.now().minusDays(1));
        given(jwtProvider.validateToken("expired-token")).willReturn(true);
        given(loadRefreshTokenPort.findByToken("expired-token")).willReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh("expired-token"))
                .isInstanceOf(OmokException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EXPIRED_TOKEN);

        then(deleteRefreshTokenPort).should().delete(expired);
    }

    // ── logout ───────────────────────────────────────────────────

    @Test
    @DisplayName("로그아웃 시 리프레시 토큰 삭제")
    void logout_deletesRefreshToken() {
        authService.logout(1L);

        then(deleteRefreshTokenPort).should().deleteByUserId(1L);
    }
}
