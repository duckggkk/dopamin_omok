package com.dopamin.omok.user.adapter.in.web;

import com.dopamin.omok.user.adapter.in.web.dto.UpdateProfileRequest;
import com.dopamin.omok.user.application.dto.UserResponse;
import com.dopamin.omok.user.application.port.in.GetUserUseCase;
import com.dopamin.omok.user.application.port.in.UpdateProfileUseCase;
import com.dopamin.omok.global.common.response.ApiResponse;
import com.dopamin.omok.global.security.userdetails.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final GetUserUseCase getUserUseCase;
    private final UpdateProfileUseCase updateProfileUseCase;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UserResponse response = getUserUseCase.getUser(userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserProfile(
            @PathVariable UUID publicId) {
        UserResponse response = getUserUseCase.getUserByPublicId(publicId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request) {
        UserResponse response = updateProfileUseCase.updateProfile(
                userDetails.getId(), request.nickname(), request.profileImageUrl());
        return ResponseEntity.ok(ApiResponse.success("프로필이 업데이트되었습니다.", response));
    }
}
