package com.dopamin.omok.global.security.jwt;

import com.dopamin.omok.global.security.userdetails.CustomUserDetails;
import com.dopamin.omok.global.security.userdetails.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 액세스 토큰 1개로부터 인증 객체를 만드는 공통 로직.
 * HTTP 필터(JwtAuthenticationFilter)와 WebSocket 인터셉터(JwtChannelInterceptor)가
 * 동일한 검증(서명 + tokenVersion 일치)을 공유하기 위해 분리했다(DRY).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticator {

    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService userDetailsService;

    /**
     * 토큰이 서명/만료 검증을 통과하고, 토큰에 담긴 tokenVersion이 DB의 현재 값과
     * 일치할 때에만 인증 객체를 반환한다. 그렇지 않으면 비어 있는 Optional.
     */
    public Optional<UsernamePasswordAuthenticationToken> authenticate(String token) {
        if (token == null || !jwtProvider.validateToken(token)) {
            return Optional.empty();
        }
        try {
            Long userId = jwtProvider.extractUserId(token);
            Long tokenVersion = jwtProvider.extractTokenVersion(token);
            CustomUserDetails userDetails = (CustomUserDetails) userDetailsService.loadUserById(userId);

            Long currentVersion = userDetails.getUser().getTokenVersion();
            if (!tokenVersion.equals(currentVersion)) {
                log.debug("Token version mismatch for userId={}: jwt={}, db={}", userId, tokenVersion, currentVersion);
                return Optional.empty();
            }

            return Optional.of(new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities()));
        } catch (Exception e) {
            log.debug("Failed to authenticate token: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
