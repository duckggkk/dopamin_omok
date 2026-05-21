package com.dopamin.omok.game.adapter.in.web;

import com.dopamin.omok.game.adapter.in.web.dto.GameMoveRequest;
import com.dopamin.omok.game.application.dto.GameMoveResponse;
import com.dopamin.omok.game.application.dto.GameRoomResponse;
import com.dopamin.omok.game.application.port.in.*;
import com.dopamin.omok.global.common.response.ApiResponse;
import com.dopamin.omok.global.security.userdetails.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/games")
@RequiredArgsConstructor
public class GameController {

    private final CreateGameRoomUseCase createGameRoomUseCase;
    private final JoinGameRoomUseCase joinGameRoomUseCase;
    private final PlaceStoneUseCase placeStoneUseCase;
    private final SurrenderUseCase surrenderUseCase;
    private final GetGameUseCase getGameUseCase;
    private final GetGameMovesUseCase getGameMovesUseCase;
    private final GetWaitingGamesUseCase getWaitingGamesUseCase;
    private final GetMyGamesUseCase getMyGamesUseCase;

    @PostMapping("/rooms")
    public ResponseEntity<ApiResponse<GameRoomResponse>> createRoom(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        GameRoomResponse response = createGameRoomUseCase.createRoom(userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("게임 방이 생성되었습니다.", response));
    }

    @PostMapping("/rooms/{roomCode}/join")
    public ResponseEntity<ApiResponse<GameRoomResponse>> joinRoom(
            @PathVariable String roomCode,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        GameRoomResponse response = joinGameRoomUseCase.joinRoom(roomCode, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("게임 방에 참가했습니다.", response));
    }

    @GetMapping("/rooms")
    public ResponseEntity<ApiResponse<Page<GameRoomResponse>>> getWaitingRooms(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<GameRoomResponse> response = getWaitingGamesUseCase.getWaitingGames(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{roomCode}")
    public ResponseEntity<ApiResponse<GameRoomResponse>> getGame(@PathVariable String roomCode) {
        GameRoomResponse response = getGameUseCase.getGame(roomCode);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{roomCode}/moves")
    public ResponseEntity<ApiResponse<List<GameMoveResponse>>> getGameMoves(
            @PathVariable String roomCode) {
        List<GameMoveResponse> moves = getGameMovesUseCase.getGameMoves(roomCode);
        return ResponseEntity.ok(ApiResponse.success(moves));
    }

    @PostMapping("/{roomCode}/moves")
    public ResponseEntity<ApiResponse<GameMoveResponse>> placeStone(
            @PathVariable String roomCode,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody GameMoveRequest request) {
        GameMoveResponse response = placeStoneUseCase.placeStone(
                roomCode, userDetails.getId(), request.row(), request.col());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/{roomCode}/surrender")
    public ResponseEntity<ApiResponse<GameRoomResponse>> surrender(
            @PathVariable String roomCode,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        GameRoomResponse response = surrenderUseCase.surrender(roomCode, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("기권하였습니다.", response));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<Page<GameRoomResponse>>> getMyGames(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<GameRoomResponse> response = getMyGamesUseCase.getMyGames(userDetails.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
