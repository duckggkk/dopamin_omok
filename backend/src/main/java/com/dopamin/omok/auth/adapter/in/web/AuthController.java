package com.dopamin.omok.auth.adapter.in.web;

import com.dopamin.omok.auth.adapter.in.web.dto.LoginRequest;
import com.dopamin.omok.auth.adapter.in.web.dto.RefreshTokenRequest;
import com.dopamin.omok.auth.adapter.in.web.dto.RegisterRequest;
import com.dopamin.omok.auth.application.dto.TokenResponse;
import com.dopamin.omok.auth.application.port.in.LoginUseCase;
import com.dopamin.omok.auth.application.port.in.LogoutUseCase;
import com.dopamin.omok.auth.application.port.in.RefreshTokenUseCase;
import com.dopamin.omok.auth.application.port.in.RegisterUseCase;
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
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<TokenResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        TokenResponse token = registerUseCase.register(
                request.email(), request.password(), request.nickname());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("회원가입이 완료되었습니다.", token));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        TokenResponse token = loginUseCase.login(request.email(), request.password());
        return ResponseEntity.ok(ApiResponse.success("로그인이 완료되었습니다.", token));
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
