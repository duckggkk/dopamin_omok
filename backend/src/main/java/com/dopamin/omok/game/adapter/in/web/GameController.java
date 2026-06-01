package com.dopamin.omok.game.adapter.in.web;

import com.dopamin.omok.game.adapter.in.web.dto.GameMoveRequest;
import com.dopamin.omok.game.application.dto.GameMoveResponse;
import com.dopamin.omok.game.application.dto.GameResponse;
import com.dopamin.omok.game.application.port.in.GetGameMovesUseCase;
import com.dopamin.omok.game.application.port.in.GetGameUseCase;
import com.dopamin.omok.game.application.port.in.PlaceStoneUseCase;
import com.dopamin.omok.game.application.port.in.SurrenderUseCase;
import com.dopamin.omok.global.common.response.ApiResponse;
import com.dopamin.omok.global.security.userdetails.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rooms/{roomCode}/game")
@RequiredArgsConstructor
public class GameController {

    private final PlaceStoneUseCase placeStoneUseCase;
    private final SurrenderUseCase surrenderUseCase;
    private final GetGameUseCase getGameUseCase;
    private final GetGameMovesUseCase getGameMovesUseCase;

    @GetMapping
    public ResponseEntity<ApiResponse<GameResponse>> getGame(@PathVariable String roomCode) {
        GameResponse response = getGameUseCase.getGame(roomCode);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/moves")
    public ResponseEntity<ApiResponse<List<GameMoveResponse>>> getGameMoves(
            @PathVariable String roomCode) {
        List<GameMoveResponse> moves = getGameMovesUseCase.getGameMoves(roomCode);
        return ResponseEntity.ok(ApiResponse.success(moves));
    }

    @PostMapping("/moves")
    public ResponseEntity<ApiResponse<GameMoveResponse>> placeStone(
            @PathVariable String roomCode,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody GameMoveRequest request) {
        GameMoveResponse response = placeStoneUseCase.placeStone(
                roomCode, userDetails.getId(), request.row(), request.col());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/surrender")
    public ResponseEntity<ApiResponse<GameResponse>> surrender(
            @PathVariable String roomCode,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        GameResponse response = surrenderUseCase.surrender(roomCode, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("기권하였습니다.", response));
    }
}
