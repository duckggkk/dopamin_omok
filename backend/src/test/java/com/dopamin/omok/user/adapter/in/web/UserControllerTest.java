package com.dopamin.omok.user.adapter.in.web;

import com.dopamin.omok.global.security.jwt.JwtProvider;
import com.dopamin.omok.global.security.userdetails.CustomUserDetailsService;
import com.dopamin.omok.support.TestSecurityConfig;
import com.dopamin.omok.support.UserFixture;
import com.dopamin.omok.support.WithCustomUser;
import com.dopamin.omok.user.adapter.in.web.dto.UpdateProfileRequest;
import com.dopamin.omok.user.application.dto.UserResponse;
import com.dopamin.omok.user.application.port.in.GetUserUseCase;
import com.dopamin.omok.user.application.port.in.UpdateProfileUseCase;
import com.dopamin.omok.user.domain.AuthProvider;
import com.dopamin.omok.user.domain.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(TestSecurityConfig.class)
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean GetUserUseCase getUserUseCase;
    @MockitoBean UpdateProfileUseCase updateProfileUseCase;
    @MockitoBean JwtProvider jwtProvider;
    @MockitoBean CustomUserDetailsService customUserDetailsService;

    private UserResponse sampleResponse(User user) {
        return new UserResponse(
                user.getPublicId(), user.getEmail(), user.getNickname(),
                null, AuthProvider.LOCAL, 0, 0, 0, 0, null);
    }

    @Test
    @DisplayName("내 프로필 조회 성공")
    @WithCustomUser(id = 1L)
    void getMyProfile_success() throws Exception {
        User user = UserFixture.create(1L);
        given(getUserUseCase.getUser(1L)).willReturn(sampleResponse(user));

        mockMvc.perform(get("/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(user.getEmail()))
                .andExpect(jsonPath("$.data.nickname").value(user.getNickname()));
    }

    @Test
    @DisplayName("publicId로 유저 프로필 조회 성공")
    @WithCustomUser(id = 2L)
    void getUserProfile_success() throws Exception {
        User user = UserFixture.create(1L);
        UUID publicId = user.getPublicId();
        given(getUserUseCase.getUserByPublicId(publicId)).willReturn(sampleResponse(user));

        mockMvc.perform(get("/users/{publicId}", publicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nickname").value(user.getNickname()));
    }

    @Test
    @DisplayName("프로필 수정 성공")
    @WithCustomUser(id = 1L)
    void updateMyProfile_success() throws Exception {
        User user = UserFixture.create(1L);
        UserResponse updated = new UserResponse(
                user.getPublicId(), user.getEmail(), "newNick",
                null, AuthProvider.LOCAL, 0, 0, 0, 0, null);
        given(updateProfileUseCase.updateProfile(1L, "newNick", null)).willReturn(updated);

        UpdateProfileRequest request = new UpdateProfileRequest("newNick", null);

        mockMvc.perform(patch("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nickname").value("newNick"));
    }

    @Test
    @DisplayName("닉네임 길이 위반 시 400")
    @WithCustomUser(id = 1L)
    void updateMyProfile_invalidNickname_returns400() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("a", null);

        mockMvc.perform(patch("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        then(updateProfileUseCase).shouldHaveNoInteractions();
    }
}
