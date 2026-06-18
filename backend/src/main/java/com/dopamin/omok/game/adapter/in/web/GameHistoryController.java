package com.dopamin.omok.game.adapter.in.web;

import com.dopamin.omok.game.application.dto.GameMoveResponse;
import com.dopamin.omok.game.application.dto.GameResponse;
import com.dopamin.omok.game.application.port.in.GetGameMovesUseCase;
import com.dopamin.omok.game.application.port.in.GetMyGamesUseCase;
import com.dopamin.omok.game.application.port.in.GetPhysicalReplayUseCase;
import com.dopamin.omok.game.physical.application.dto.PhysicalReplayData;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class GameHistoryController {

    private final GetMyGamesUseCase getMyGamesUseCase;
    private final GetGameMovesUseCase getGameMovesUseCase;
    private final GetPhysicalReplayUseCase getPhysicalReplayUseCase;

    @GetMapping("/games/my")
    public ResponseEntity<ApiResponse<Page<GameResponse>>> getMyGames(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<GameResponse> response = getMyGamesUseCase.getMyGames(userDetails.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 기보 보기 — 종료된 내 게임의 착수 목록(참가자만 조회 가능). */
    @GetMapping("/games/{gameId}/moves")
    public ResponseEntity<ApiResponse<List<GameMoveResponse>>> getGameMoves(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long gameId) {
        List<GameMoveResponse> response = getGameMovesUseCase.getGameMovesByGameId(gameId, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 피지컬 오목 리플레이 — 기록이 없으면(일반 오목 등) data=null. 참가자만 조회 가능. */
    @GetMapping("/games/{gameId}/physical-replay")
    public ResponseEntity<ApiResponse<PhysicalReplayData>> getPhysicalReplay(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long gameId) {
        PhysicalReplayData replay = getPhysicalReplayUseCase.getReplay(gameId, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(replay));
    }

    @GetMapping("/users/{publicId}/games")
    public ResponseEntity<ApiResponse<Page<GameResponse>>> getPublicGames(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID publicId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<GameResponse> response = getMyGamesUseCase.getPublicGames(publicId, userDetails.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/users/{publicId}/games/{gameId}/moves")
    public ResponseEntity<ApiResponse<List<GameMoveResponse>>> getPublicGameMoves(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID publicId,
            @PathVariable Long gameId) {
        List<GameMoveResponse> response = getGameMovesUseCase
                .getPublicGameMovesByGameId(publicId, gameId, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/users/{publicId}/games/{gameId}/physical-replay")
    public ResponseEntity<ApiResponse<PhysicalReplayData>> getPublicPhysicalReplay(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID publicId,
            @PathVariable Long gameId) {
        PhysicalReplayData replay = getPhysicalReplayUseCase.getPublicReplay(publicId, gameId, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(replay));
    }
}
