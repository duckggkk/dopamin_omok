package com.dopamin.omok.global.security.jwt;

import com.dopamin.omok.global.security.principal.AuthUser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 액세스 토큰 1개로부터 인증 객체를 만드는 공통 로직.
 * HTTP 필터(JwtAuthenticationFilter)와 WebSocket 인터셉터(JwtChannelInterceptor)가
 * 동일한 검증을 공유하기 위해 분리했다(DRY).
 * <p>
 * <b>검증 범위는 서명과 만료뿐이다(stateless).</b> DB 조회를 하지 않는다.
 * 예전에는 여기서 {@code users} 를 조회해 JWT 의 tokenVersion 과 DB 값을 비교했지만,
 * Bearer 토큰이 붙은 모든 요청마다 DB 를 한 번씩 치는 구조라 제거했다.
 * 판단 근거는 {@code dev-log/20260708 jwt 검증구조 변경.md} 참고.
 * <p>
 * 그 대가로 <b>access token 즉시 무효화는 지원하지 않는다.</b>
 * 로그아웃이나 권한 변경 뒤에도 이미 발급된 access token 은 만료(30분)까지 유효하다.
 * refresh token 은 Redis 로 관리되므로 재발급 자체는 즉시 차단된다.
 * 즉시 무효화가 필요해지면(예: 결제 도입) DB 조회를 되돌리지 말고
 * Redis 기반 tokenVersion 또는 jti blacklist 로 구현할 것.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticator {

    private final JwtProvider jwtProvider;

    /**
     * 토큰이 서명/만료 검증을 통과하면 claim 만으로 {@link AuthUser} 를 구성해 반환한다.
     * 통과하지 못하면 비어 있는 Optional.
     * <p>
     * 토큰에 tokenVersion claim 이 들어 있긴 하지만 여기서 검사하지 않는다(위 클래스 주석 참고).
     */
    // Throw를 던지지 않는 이유는 호출부 (HTTP필터 / 웹소켓 인터셉터)에서 정하기 위해서
    // HTTP필터는 미인증(public) API가 존재하기 때문
    public Optional<UsernamePasswordAuthenticationToken> authenticate(String token) {
        if (token == null || !jwtProvider.validateToken(token)) {
            return Optional.empty();
        }
        try {
            AuthUser authUser = new AuthUser(
                    jwtProvider.extractUserId(token),
                    jwtProvider.extractPublicId(token),
                    jwtProvider.extractEmail(token),
                    jwtProvider.extractNickName(token),
                    jwtProvider.extractRole(token));

            return Optional.of(new UsernamePasswordAuthenticationToken(
                    authUser, null, authUser.getAuthorities()));
        } catch (Exception e) {
            log.debug("Failed to authenticate token: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
