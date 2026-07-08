package com.dopamin.omok.auth.adapter.in.web;

import com.dopamin.omok.auth.application.dto.OAuth2LoginResult;
import com.dopamin.omok.auth.application.port.in.OAuth2LoginUseCase;
import com.dopamin.omok.auth.config.GoogleOAuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 구글 로그인(백엔드 인가 코드 방식)의 진입점과 콜백.
 *
 * <p>흐름: 프론트가 {@code /api/auth/oauth2/google} 로 전체 페이지 이동 → 여기서 구글 동의화면으로
 * 302 리다이렉트 → 구글이 {@code /callback?code=...} 으로 되돌려보냄 → 서비스가 토큰을 발급하고
 * 프론트 콜백 페이지로 토큰을 URL 프래그먼트(#)에 실어 리다이렉트한다(프래그먼트는 서버 로그/리퍼러에
 * 남지 않음).
 *
 * <p><b>CSRF(로그인 위조) 방지</b> — 진입 시 임의의 state 를 HttpOnly 쿠키로 심고, 콜백에서 쿼리의
 * state 와 대조한다. 일치하지 않으면 거부한다.
 */
@Slf4j
@RestController
@RequestMapping("/auth/oauth2")
@RequiredArgsConstructor
public class OAuth2Controller {

    private static final String GOOGLE_AUTH_URI = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String STATE_COOKIE = "oauth_state";
    private static final int STATE_TTL_SECONDS = 300; // 5분

    private final OAuth2LoginUseCase oAuth2LoginUseCase;
    private final GoogleOAuthProperties googleProperties;

    // 콜백 후 토큰을 넘겨줄 프론트 주소. prod/local 은 yml 값이 주입되고, 값이 없는 환경(docker/test)은
    // 기본값으로 떨어져 빈 생성이 실패하지 않게 한다.
    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    /** 구글 동의화면으로 보낸다. state 를 쿠키에 심어 콜백에서 위조 여부를 검증한다. */
    @GetMapping("/google")
    public void startGoogle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String state = UUID.randomUUID().toString().replace("-", ""); // csrf 방지용 난수
        //state를 쿠키에도 담고 url에도 담아서 콜백url에서 비교
        response.addHeader(HttpHeaders.SET_COOKIE, buildStateCookie(state, request.isSecure(), STATE_TTL_SECONDS));

        //쿼리 파라 변수명은 OAuth/OIDC 표준 + 구글 규격
        String authUrl = UriComponentsBuilder.fromUriString(GOOGLE_AUTH_URI)
                .queryParam("client_id", googleProperties.clientId())
                .queryParam("redirect_uri", googleProperties.redirectUri())
                .queryParam("response_type", "code") // OAuth 표준, 인가코드방식 쓰겠다
                .queryParam("scope", "openid email profile") //구글에서 받을것들, id,email,profile
                .queryParam("state", state) // csrf 방지용 난수
                .queryParam("prompt", "select_account") // 구글 로그인 ui설정, 계정선택화면보여주기
                .build().toUriString();
        response.sendRedirect(authUrl); //302 리다이렉트
    }

    /** 구글 콜백. 토큰을 발급해 프론트 콜백 페이지로 넘기고, 실패하면 로그인 화면으로 보낸다. */
    @GetMapping("/google/callback")
    public void googleCallback(@RequestParam(required = false) String code,
                               @RequestParam(required = false) String state,
                               @CookieValue(name = STATE_COOKIE, required = false) String cookieState,
                               HttpServletRequest request,
                               HttpServletResponse response) throws IOException {
        // state 쿠키는 1회용 — 성공/실패와 무관하게 즉시 만료시킨다.
        response.addHeader(HttpHeaders.SET_COOKIE, buildStateCookie("", request.isSecure(), 0));

        if (code == null || state == null || cookieState == null || !state.equals(cookieState)) {
            log.warn("구글 OAuth state 불일치 또는 code 누락 — 로그인 거부");
            response.sendRedirect(frontendUrl + "/login?error=oauth");
            return;
        }

        try {
            OAuth2LoginResult result = oAuth2LoginUseCase.loginWithGoogle(code);
            String redirect = frontendUrl + "/oauth/callback#accessToken=" 
                    + enc(result.tokens().accessToken())
                    + "&refreshToken=" + enc(result.tokens().refreshToken())
                    + "&newUser=" + result.newUser();
            response.sendRedirect(redirect);
        } catch (Exception e) {
            log.warn("구글 OAuth 로그인 실패: {}", e.getMessage());
            response.sendRedirect(frontendUrl + "/login?error=oauth");
        }
    }

    private String buildStateCookie(String value, boolean secure, int maxAgeSeconds) {
        return ResponseCookie.from(STATE_COOKIE, value)
                .httpOnly(true)
                .secure(secure)         // https 면 Secure 부여(로컬 http 에선 자동 생략)
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite("Lax")        // 구글→콜백은 top-level GET 이라 Lax 로 충분히 전달됨
                .build().toString();
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
