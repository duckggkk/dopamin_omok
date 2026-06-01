package com.dopamin.omok.game.adapter.in.web;

import com.dopamin.omok.game.application.dto.GameResponse;
import com.dopamin.omok.game.application.port.in.GetMyGamesUseCase;
import com.dopamin.omok.global.common.response.ApiResponse;
import com.dopamin.omok.global.security.userdetails.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GameHistoryController {

    private final GetMyGamesUseCase getMyGamesUseCase;

    @GetMapping("/games/my")
    public ResponseEntity<ApiResponse<Page<GameResponse>>> getMyGames(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<GameResponse> response = getMyGamesUseCase.getMyGames(userDetails.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
