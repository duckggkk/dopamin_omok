package com.dopamin.omok.game.adapter.in.web;

import com.dopamin.omok.game.application.dto.GameMoveResponse;
import com.dopamin.omok.game.application.dto.GameSummaryResponse;
import com.dopamin.omok.game.application.port.in.GetGameMovesUseCase;
import com.dopamin.omok.game.application.port.in.GetMyGamesUseCase;
import com.dopamin.omok.game.application.port.in.GetPhysicalReplayUseCase;
import com.dopamin.omok.game.physical.application.dto.PhysicalReplayData;
import com.dopamin.omok.global.common.response.ApiResponse;
import com.dopamin.omok.global.security.principal.AuthUser;
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

    /** 내 전적 목록 — 상대의 레이팅·전적·칭호까지 딸려가지 않도록 요약 형태로 내려준다. */
    @GetMapping("/games/my")
    public ResponseEntity<ApiResponse<Page<GameSummaryResponse>>> getMyGames(
            @AuthenticationPrincipal AuthUser userDetails,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<GameSummaryResponse> response = getMyGamesUseCase.getMyGames(userDetails.id(), pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 기보 보기 — 종료된 내 게임의 착수 목록(참가자만 조회 가능). */
    @GetMapping("/games/{gameId}/moves")
    public ResponseEntity<ApiResponse<List<GameMoveResponse>>> getGameMoves(
            @AuthenticationPrincipal AuthUser userDetails,
            @PathVariable Long gameId) {
        List<GameMoveResponse> response = getGameMovesUseCase.getGameMovesByGameId(gameId, userDetails.id());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 피지컬 오목 리플레이 — 기록이 없으면(일반 오목 등) data=null. 참가자만 조회 가능. */
    @GetMapping("/games/{gameId}/physical-replay")
    public ResponseEntity<ApiResponse<PhysicalReplayData>> getPhysicalReplay(
            @AuthenticationPrincipal AuthUser userDetails,
            @PathVariable Long gameId) {
        PhysicalReplayData replay = getPhysicalReplayUseCase.getReplay(gameId, userDetails.id());
        return ResponseEntity.ok(ApiResponse.success(replay));
    }

    /* 전적 목록보기 */
    @GetMapping("/users/{publicId}/games")
    public ResponseEntity<ApiResponse<Page<GameSummaryResponse>>> getPublicGames(
            @AuthenticationPrincipal AuthUser userDetails,
            @PathVariable UUID publicId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<GameSummaryResponse> response = getMyGamesUseCase.getPublicGames(publicId, userDetails.id(), pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    //기보 보기, 다른사람도 볼수있음
    @GetMapping("/users/{publicId}/games/{gameId}/moves")
    public ResponseEntity<ApiResponse<List<GameMoveResponse>>> getPublicGameMoves(
            @AuthenticationPrincipal AuthUser userDetails,
            @PathVariable UUID publicId,
            @PathVariable Long gameId) {
        List<GameMoveResponse> response = getGameMovesUseCase
                .getPublicGameMovesByGameId(publicId, gameId, userDetails.id());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/users/{publicId}/games/{gameId}/physical-replay")
    public ResponseEntity<ApiResponse<PhysicalReplayData>> getPublicPhysicalReplay(
            @AuthenticationPrincipal AuthUser userDetails,
            @PathVariable UUID publicId,
            @PathVariable Long gameId) {
        PhysicalReplayData replay = getPhysicalReplayUseCase.getPublicReplay(publicId, gameId, userDetails.id());
        return ResponseEntity.ok(ApiResponse.success(replay));
    }
}
