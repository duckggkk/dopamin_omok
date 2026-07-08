package com.dopamin.omok.auth.adapter.in.web.support;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

/**
 * 웹 클라이언트용 리프레시 토큰 HttpOnly 쿠키 처리.
 * <p>
 * 앱은 리프레시 토큰을 응답 body 로 받아 OS 보안저장소(Keychain/Keystore)에 보관하지만,
 * 웹(브라우저)은 XSS 로 토큰이 탈취되는 것을 막기 위해 <b>JS 가 읽을 수 없는 HttpOnly 쿠키</b>에 담는다.
 * 클라이언트는 요청에 {@code X-Client-Type: web} 헤더를 붙여 자신이 웹임을 알린다(헤더가 없으면 앱으로 간주).
 * <p>
 * 쿠키 경로는 {@code /api/auth} — context-path({@code /api}) + 인증 경로. 리프레시/로그아웃 요청에만
 * 자동 전송되고 그 외 API 요청에는 실리지 않는다. Secure/SameSite 는 OAuth state 쿠키와 동일 정책.
 */
public final class RefreshTokenCookie {

    /** 클라이언트가 자신의 종류(web/app)를 알리는 헤더. */
    public static final String CLIENT_TYPE_HEADER = "X-Client-Type";
    /** 웹 클라이언트 식별값. */
    public static final String WEB_CLIENT = "web";
    /** 리프레시 토큰 쿠키 이름. */
    public static final String NAME = "refresh_token";

    /** 리프레시/로그아웃에만 쿠키가 전송되도록 경로를 좁힌다. context-path(/api) 포함 브라우저 기준 경로. */
    private static final String PATH = "/api/auth";

    private RefreshTokenCookie() {
    }

    /** 요청이 웹 클라이언트({@code X-Client-Type: web})인지. 앱/미지정은 false → 기존 body 방식 유지. */
    public static boolean isWebClient(HttpServletRequest request) {
        return WEB_CLIENT.equalsIgnoreCase(request.getHeader(CLIENT_TYPE_HEADER));
    }

    /** 리프레시 토큰을 담은 HttpOnly 쿠키. maxAge 는 리프레시 토큰 수명과 맞춘다. */
    public static ResponseCookie of(String token, long maxAgeSeconds, boolean secure) {
        return base(token, secure)
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
    }

    /** 로그아웃 시 즉시 지우는 빈 쿠키. 발급할 때와 같은 name/path 여야 브라우저가 삭제한다. */
    public static ResponseCookie expired(boolean secure) {
        return base("", secure)
                .maxAge(0)
                .build();
    }

    private static ResponseCookie.ResponseCookieBuilder base(String value, boolean secure) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)      // JS(document.cookie)로 읽을 수 없음 → XSS 탈취 방지
                .secure(secure)      // https 면 Secure 부여(로컬 http 는 자동 생략)
                .path(PATH)
                .sameSite("Lax");    // 같은 사이트 XHR 엔 전송, 크로스사이트 CSRF 는 차단
    }
}