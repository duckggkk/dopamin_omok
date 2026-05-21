package com.dopamin.omok.auth.adapter.in.web;

import com.dopamin.omok.auth.adapter.in.web.dto.LoginRequest;
import com.dopamin.omok.auth.adapter.in.web.dto.RefreshTokenRequest;
import com.dopamin.omok.auth.adapter.in.web.dto.RegisterRequest;
import com.dopamin.omok.auth.application.dto.TokenResponse;
import com.dopamin.omok.auth.application.port.in.LoginUseCase;
import com.dopamin.omok.auth.application.port.in.LogoutUseCase;
import com.dopamin.omok.auth.application.port.in.RefreshTokenUseCase;
import com.dopamin.omok.auth.application.port.in.RegisterUseCase;
import com.dopamin.omok.global.common.exception.ErrorCode;
import com.dopamin.omok.global.common.exception.OmokException;
import com.dopamin.omok.global.security.jwt.JwtProvider;
import com.dopamin.omok.global.security.userdetails.CustomUserDetailsService;
import com.dopamin.omok.support.TestSecurityConfig;
import com.dopamin.omok.support.WithCustomUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(TestSecurityConfig.class)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean RegisterUseCase registerUseCase;
    @MockitoBean LoginUseCase loginUseCase;
    @MockitoBean RefreshTokenUseCase refreshTokenUseCase;
    @MockitoBean LogoutUseCase logoutUseCase;
    @MockitoBean JwtProvider jwtProvider;
    @MockitoBean CustomUserDetailsService customUserDetailsService;

    private static final TokenResponse SAMPLE_TOKEN =
            TokenResponse.of("access.token.value", "refresh.token.value", 3_600_000L);

    @Test
    @DisplayName("회원가입 성공 - 201")
    void register_success() throws Exception {
        RegisterRequest request = new RegisterRequest("user@test.com", "password1!", "nick");
        given(registerUseCase.register("user@test.com", "password1!", "nick"))
                .willReturn(SAMPLE_TOKEN);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("회원가입 - 이메일 형식 오류 시 400")
    void register_invalidEmail_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("not-an-email", "password1!", "nick");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("회원가입 - 비밀번호 정책 위반 시 400")
    void register_invalidPassword_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("user@test.com", "short", "nick");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("로그인 성공 - 200")
    void login_success() throws Exception {
        LoginRequest request = new LoginRequest("user@test.com", "password1!");
        given(loginUseCase.login("user@test.com", "password1!")).willReturn(SAMPLE_TOKEN);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access.token.value"));
    }

    @Test
    @DisplayName("로그인 - 잘못된 자격증명 시 401")
    void login_invalidCredentials_returns401() throws Exception {
        LoginRequest request = new LoginRequest("user@test.com", "wrongPass");
        given(loginUseCase.login("user@test.com", "wrongPass"))
                .willThrow(new OmokException(ErrorCode.INVALID_CREDENTIALS));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("토큰 갱신 성공 - 200")
    void refresh_success() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("some.refresh.token");
        given(refreshTokenUseCase.refresh("some.refresh.token")).willReturn(SAMPLE_TOKEN);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access.token.value"));
    }

    @Test
    @DisplayName("로그아웃 성공 - 200")
    @WithCustomUser(id = 1L)
    void logout_success() throws Exception {
        willDoNothing().given(logoutUseCase).logout(1L);

        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
