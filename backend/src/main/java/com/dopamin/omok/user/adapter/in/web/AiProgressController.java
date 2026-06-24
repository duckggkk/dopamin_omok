package com.dopamin.omok.user.adapter.in.web;

import com.dopamin.omok.global.common.response.ApiResponse;
import com.dopamin.omok.global.security.userdetails.CustomUserDetails;
import com.dopamin.omok.user.adapter.in.web.dto.AiClearRequest;
import com.dopamin.omok.user.application.dto.AiProgressResponse;
import com.dopamin.omok.user.application.port.in.AiProgressUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 싱글플레이 AI 사다리 진척 API. 인증 사용자 전용(SecurityConfig anyRequest().authenticated()).
 * AI 대국 승패/레이팅과 무관하며, "어느 단계까지 깼는지"만 계정에 저장한다.
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiProgressController {

    private final AiProgressUseCase aiProgressUseCase;

    /** 내 AI 사다리 진척 조회. */
    @GetMapping("/progress")
    public ResponseEntity<ApiResponse<AiProgressResponse>> getProgress(
            @AuthenticationPrincipal CustomUserDetails me) {
        return ResponseEntity.ok(ApiResponse.success(aiProgressUseCase.getProgress(me.getId())));
    }

    /** 한 단계 클리어 보고 → 갱신된 진척 반환. */
    @PostMapping("/clear")
    public ResponseEntity<ApiResponse<AiProgressResponse>> recordClear(
            @AuthenticationPrincipal CustomUserDetails me,
            @Valid @RequestBody AiClearRequest request) {
        return ResponseEntity.ok(ApiResponse.success(aiProgressUseCase.recordClear(me.getId(), request.level())));
    }
}
