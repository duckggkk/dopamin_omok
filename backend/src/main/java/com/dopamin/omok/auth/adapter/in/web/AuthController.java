package com.dopamin.omok.auth.adapter.in.web;

import com.dopamin.omok.auth.adapter.in.web.dto.LoginRequest;
import com.dopamin.omok.auth.adapter.in.web.dto.RefreshTokenRequest;
import com.dopamin.omok.auth.adapter.in.web.dto.RegisterRequest;
import com.dopamin.omok.auth.adapter.in.web.dto.ResendVerificationRequest;
import com.dopamin.omok.auth.adapter.in.web.dto.VerifyEmailRequest;
import com.dopamin.omok.auth.application.dto.TokenResponse;
import com.dopamin.omok.auth.application.port.in.*;
import com.dopamin.omok.global.common.response.ApiResponse;
import com.dopamin.omok.global.security.userdetails.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(
            @Valid @RequestBody RegisterRequest request) {
        registerUseCase.register(request.email(), request.password(), request.nickname());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("인증 이메일을 발송했습니다. 이메일을 확인해주세요."));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request) {
        verifyEmailUseCase.verifyEmail(request.email(), request.code());
        return ResponseEntity.ok(ApiResponse.success("이메일 인증이 완료되었습니다."));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        resendVerificationEmailUseCase.resendVerificationEmail(request.email());
        return ResponseEntity.ok(ApiResponse.success("인증 이메일을 재발송했습니다."));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        TokenResponse token = loginUseCase.login(request.email(), request.password());
        return ResponseEntity.ok(ApiResponse.success("로그인이 완료되었습니다.", token));
    }

    @PostMapping("/guest")
    public ResponseEntity<ApiResponse<TokenResponse>> guestLogin() {
        TokenResponse token = guestLoginUseCase.loginAsGuest();
        return ResponseEntity.ok(ApiResponse.success("게스트로 시작합니다.", token));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        TokenResponse token = refreshTokenUseCase.refresh(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.success("토큰이 갱신되었습니다.", token));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        logoutUseCase.logout(userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("로그아웃이 완료되었습니다."));
    }
}
