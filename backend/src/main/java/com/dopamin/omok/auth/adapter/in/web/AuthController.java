package com.dopamin.omok.auth.adapter.in.web;

import com.dopamin.omok.auth.adapter.in.web.dto.LoginRequest;
import com.dopamin.omok.auth.adapter.in.web.dto.RefreshTokenRequest;
import com.dopamin.omok.auth.adapter.in.web.dto.RegisterRequest;
import com.dopamin.omok.auth.adapter.in.web.dto.ResendVerificationRequest;
import com.dopamin.omok.auth.adapter.in.web.dto.VerifyEmailRequest;
import com.dopamin.omok.auth.adapter.in.web.support.RefreshTokenCookie;
import com.dopamin.omok.auth.application.dto.TokenResponse;
import com.dopamin.omok.auth.application.port.in.*;
import com.dopamin.omok.global.common.exception.ErrorCode;
import com.dopamin.omok.global.common.exception.OmokException;
import com.dopamin.omok.global.common.response.ApiResponse;
import com.dopamin.omok.global.security.principal.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RegisterUseCase registerUseCase;
    private final LoginUseCase loginUseCase;
    private final GuestLoginUseCase guestLoginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final VerifyEmailUseCase verifyEmailUseCase;
    private final ResendVerificationEmailUseCase resendVerificationEmailUseCase;

    //회원가입
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(
            @Valid @RequestBody RegisterRequest request) {
        registerUseCase.register(request.email(), request.password(), request.nickname());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("인증 이메일을 발송했습니다. 이메일을 확인해주세요."));
    }

    //회원가입 인증확인
    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request) {
        verifyEmailUseCase.verifyEmail(request.email(), request.code());
        return ResponseEntity.ok(ApiResponse.success("이메일 인증이 완료되었습니다."));
    }

    //이메일 재인증
    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        resendVerificationEmailUseCase.resendVerificationEmail(request.email());
        return ResponseEntity.ok(ApiResponse.success("인증 이메일을 재발송했습니다."));
    }

    //로그인
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        TokenResponse token = loginUseCase.login(request.email(), request.password());
        return tokenResponse(token, "로그인이 완료되었습니다.", httpRequest);
    }

    //게스트 회원가입
    @PostMapping("/guest")
    public ResponseEntity<ApiResponse<TokenResponse>> guestLogin(HttpServletRequest httpRequest) {
        TokenResponse token = guestLoginUseCase.loginAsGuest();
        return tokenResponse(token, "게스트로 시작합니다.", httpRequest);
    }

    //리프레쉬토큰 재발급
    // 웹은 리프레시 토큰을 HttpOnly 쿠키에서, 앱은 요청 body 에서 읽는다(X-Client-Type 로 구분).
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @RequestBody(required = false) RefreshTokenRequest request,
            @CookieValue(name = RefreshTokenCookie.NAME, required = false) String cookieToken,
            HttpServletRequest httpRequest) {
        boolean web = RefreshTokenCookie.isWebClient(httpRequest);
        String refreshToken = web ? cookieToken : (request != null ? request.refreshToken() : null);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new OmokException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }
        TokenResponse token = refreshTokenUseCase.refresh(refreshToken);
        return tokenResponse(token, "토큰이 갱신되었습니다.", httpRequest);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal AuthUser userDetails,
            HttpServletRequest httpRequest) {
        logoutUseCase.logout(userDetails.id());
        // 웹은 서버 세션 삭제와 함께 브라우저의 리프레시 쿠키도 만료시킨다.
        if (RefreshTokenCookie.isWebClient(httpRequest)) {
            ResponseCookie cleared = RefreshTokenCookie.expired(httpRequest.isSecure());
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cleared.toString())
                    .body(ApiResponse.success("로그아웃이 완료되었습니다."));
        }
        return ResponseEntity.ok(ApiResponse.success("로그아웃이 완료되었습니다."));
    }

    /**
     * 토큰 응답 분기. 웹(X-Client-Type: web)이면 리프레시 토큰을 HttpOnly 쿠키로 내리고 body 에서는 제외한다.
     * 앱이면 기존대로 액세스·리프레시를 모두 body 로 내린다.
     */
    private ResponseEntity<ApiResponse<TokenResponse>> tokenResponse(
            TokenResponse token, String message, HttpServletRequest httpRequest) {
        
        //if 웹이면
        if (RefreshTokenCookie.isWebClient(httpRequest)) {
            // expiresIn(ms) = 리프레시 토큰 수명 → 쿠키 maxAge(초)로 환산
            ResponseCookie cookie = RefreshTokenCookie.of(
                    token.refreshToken(), token.expiresIn() / 1000, httpRequest.isSecure());
            TokenResponse body = TokenResponse.of(token.accessToken(), null, token.expiresIn());
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(ApiResponse.success(message, body));
        }
        //웹 아니면
        return ResponseEntity.ok(ApiResponse.success(message, token));
    }
}
