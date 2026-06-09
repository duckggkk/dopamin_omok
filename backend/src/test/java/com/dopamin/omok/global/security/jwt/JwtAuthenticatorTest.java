package com.dopamin.omok.global.security.jwt;

import com.dopamin.omok.global.security.userdetails.CustomUserDetails;
import com.dopamin.omok.global.security.userdetails.CustomUserDetailsService;
import com.dopamin.omok.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticatorTest {

    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private CustomUserDetailsService userDetailsService;
    @InjectMocks
    private JwtAuthenticator jwtAuthenticator;

    private final User user = User.createLocalUser("user@x.com", "pw", "nick"); // tokenVersion = 0

    @Test
    @DisplayName("유효한 토큰 + 일치하는 tokenVersion 이면 인증 객체 반환")
    void validTokenWithMatchingVersion() {
        when(jwtProvider.validateToken("token")).thenReturn(true);
        when(jwtProvider.extractUserId("token")).thenReturn(1L);
        when(jwtProvider.extractTokenVersion("token")).thenReturn(0L);
        when(userDetailsService.loadUserById(1L)).thenReturn(new CustomUserDetails(user));

        assertThat(jwtAuthenticator.authenticate("token")).isPresent();
    }

    @Test
    @DisplayName("tokenVersion 불일치(무효화된 토큰)면 빈 결과")
    void versionMismatchReturnsEmpty() {
        when(jwtProvider.validateToken("token")).thenReturn(true);
        when(jwtProvider.extractUserId("token")).thenReturn(1L);
        when(jwtProvider.extractTokenVersion("token")).thenReturn(5L); // DB는 0
        lenient().when(userDetailsService.loadUserById(1L)).thenReturn(new CustomUserDetails(user));

        assertThat(jwtAuthenticator.authenticate("token")).isEmpty();
    }

    @Test
    @DisplayName("서명/형식이 무효한 토큰이면 DB 조회 없이 빈 결과")
    void invalidTokenReturnsEmpty() {
        when(jwtProvider.validateToken("bad")).thenReturn(false);

        assertThat(jwtAuthenticator.authenticate("bad")).isEmpty();
        verify(userDetailsService, never()).loadUserById(org.mockito.ArgumentMatchers.anyLong());
    }
}
