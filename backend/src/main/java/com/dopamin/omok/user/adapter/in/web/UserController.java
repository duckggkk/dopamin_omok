package com.dopamin.omok.user.adapter.in.web;

import com.dopamin.omok.user.adapter.in.web.dto.UpdateProfileRequest;
import com.dopamin.omok.user.application.dto.RankingMode;
import com.dopamin.omok.user.application.dto.RankingResponse;
import com.dopamin.omok.user.application.dto.PublicUserResponse;
import com.dopamin.omok.user.application.dto.UserResponse;
import com.dopamin.omok.user.application.port.in.GetRankingUseCase;
import com.dopamin.omok.user.application.port.in.GetUserUseCase;
import com.dopamin.omok.user.application.port.in.UpdateProfileUseCase;
import com.dopamin.omok.global.common.response.ApiResponse;
import com.dopamin.omok.global.security.principal.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final GetUserUseCase getUserUseCase;
    private final UpdateProfileUseCase updateProfileUseCase;
    private final GetRankingUseCase getRankingUseCase;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile(
            @AuthenticationPrincipal AuthUser userDetails) {
        UserResponse response = getUserUseCase.getUser(userDetails.id());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<ApiResponse<PublicUserResponse>> getUserProfile(
            @AuthenticationPrincipal AuthUser userDetails,
            @PathVariable UUID publicId) {
        PublicUserResponse response = getUserUseCase.getUserByPublicId(publicId, userDetails.id());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateMyProfile(
            @AuthenticationPrincipal AuthUser userDetails,
            @Valid @RequestBody UpdateProfileRequest request) {
        UserResponse response = updateProfileUseCase.updateProfile(
                userDetails.id(), request.nickname(), request.profileImageUrl(), request.profilePrivate());
        return ResponseEntity.ok(ApiResponse.success("프로필이 업데이트되었습니다.", response));
    }

    /** 랭킹: 상위 limit명(기본 20, 최대 100). mode=TOTAL(기본)/CLASSIC/PHYSICAL 로 탭 전환. */
    @GetMapping("/ranking")
    public ResponseEntity<ApiResponse<List<RankingResponse>>> getRanking(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "TOTAL") RankingMode mode) {
        return ResponseEntity.ok(ApiResponse.success(getRankingUseCase.getRanking(limit, mode)));
    }
}
