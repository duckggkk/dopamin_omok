package com.dopamin.omok.game.adapter.in.web;

import com.dopamin.omok.game.adapter.in.web.dto.GameMoveRequest;
import com.dopamin.omok.game.application.dto.GameMoveResponse;
import com.dopamin.omok.game.application.dto.GameRoomResponse;
import com.dopamin.omok.game.application.port.in.PlaceStoneUseCase;
import com.dopamin.omok.game.application.port.in.SurrenderUseCase;
import com.dopamin.omok.global.common.response.ApiResponse;
import com.dopamin.omok.global.security.userdetails.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class GameWebSocketController {

    private final PlaceStoneUseCase placeStoneUseCase;
    private final SurrenderUseCase surrenderUseCase;

    @MessageMapping("/game/{roomCode}/move")
    @SendTo("/topic/game/{roomCode}")
    public ApiResponse<GameMoveResponse> handleMove(
            @DestinationVariable String roomCode,
            @Valid GameMoveRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            GameMoveResponse response = placeStoneUseCase.placeStone(
                    roomCode, userDetails.getId(), request.row(), request.col());
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.warn("WebSocket move error for room {}: {}", roomCode, e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @MessageMapping("/game/{roomCode}/surrender")
    @SendTo("/topic/game/{roomCode}/status")
    public ApiResponse<GameRoomResponse> handleSurrender(
            @DestinationVariable String roomCode,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            GameRoomResponse response = surrenderUseCase.surrender(roomCode, userDetails.getId());
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.warn("WebSocket surrender error for room {}: {}", roomCode, e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }
}
