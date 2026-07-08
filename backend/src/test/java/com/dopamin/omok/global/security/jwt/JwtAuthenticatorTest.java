package com.dopamin.omok.global.security.jwt;

import com.dopamin.omok.global.security.principal.AuthUser;
import com.dopamin.omok.user.domain.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticatorTest {

    @Mock
    private JwtProvider jwtProvider;

    @InjectMocks
    private JwtAuthenticator jwtAuthenticator;

    @Test
    @DisplayName("유효한 토큰이면 JWT claim으로 AuthUser principal을 만든다")
    void validTokenCreatesAuthUserPrincipal() {
        UUID publicId = UUID.randomUUID();
        when(jwtProvider.validateToken("token")).thenReturn(true);
        when(jwtProvider.extractUserId("token")).thenReturn(1L);
        when(jwtProvider.extractPublicId("token")).thenReturn(publicId);
        when(jwtProvider.extractEmail("token")).thenReturn("user@x.com");
        when(jwtProvider.extractNickName("token")).thenReturn("nick");
        when(jwtProvider.extractRole("token")).thenReturn(UserRole.USER);

        assertThat(jwtAuthenticator.authenticate("token"))
                .get()
                .satisfies(authentication -> {
                    assertThat(authentication).isInstanceOf(UsernamePasswordAuthenticationToken.class);
                    assertThat(authentication.getCredentials()).isNull();
                    assertThat(authentication.getPrincipal()).isEqualTo(
                            new AuthUser(1L, publicId, "user@x.com", "nick", UserRole.USER));
                });
    }

    @Test
    @DisplayName("claim 추출에 실패하면 빈 결과를 반환한다")
    void claimExtractionFailureReturnsEmpty() {
        when(jwtProvider.validateToken("token")).thenReturn(true);
        when(jwtProvider.extractUserId("token")).thenThrow(new IllegalArgumentException("invalid subject"));

        assertThat(jwtAuthenticator.authenticate("token")).isEmpty();
    }

    @Test
    @DisplayName("서명/형식이 무효한 토큰이면 빈 결과를 반환한다")
    void invalidTokenReturnsEmpty() {
        when(jwtProvider.validateToken("bad")).thenReturn(false);

        assertThat(jwtAuthenticator.authenticate("bad")).isEmpty();
    }
}
