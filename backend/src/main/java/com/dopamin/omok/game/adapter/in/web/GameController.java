package com.dopamin.omok.game.adapter.in.web;

import com.dopamin.omok.game.application.dto.GameMoveResponse;
import com.dopamin.omok.game.application.port.in.GetGameMovesUseCase;
import com.dopamin.omok.global.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 실시간 플레이(착수/기권/게임 조회)는 전부 WebSocket(GameWebSocketController)으로 처리한다.
 * 여기는 새로고침 시 판 복원용 REST 조회만 남긴다.
 */
@RestController
@RequestMapping("/rooms/{roomCode}/game")
@RequiredArgsConstructor
public class GameController {

    private final GetGameMovesUseCase getGameMovesUseCase;

    // 새로고침 시, 판 복원
    @GetMapping("/moves")
    public ResponseEntity<ApiResponse<List<GameMoveResponse>>> getGameMoves(
            @PathVariable String roomCode) {
        List<GameMoveResponse> moves = getGameMovesUseCase.getGameMoves(roomCode);
        return ResponseEntity.ok(ApiResponse.success(moves));
    }
}
