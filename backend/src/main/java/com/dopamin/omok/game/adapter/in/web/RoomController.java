package com.dopamin.omok.game.adapter.in.web;

import com.dopamin.omok.game.adapter.in.web.dto.CreateRoomRequest;
import com.dopamin.omok.game.application.dto.RoomResponse;
import com.dopamin.omok.game.application.port.in.*;
import com.dopamin.omok.game.domain.GameType;
import com.dopamin.omok.global.common.response.ApiResponse;
import com.dopamin.omok.global.security.principal.AuthUser;
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

@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final CreateRoomUseCase createRoomUseCase;
    private final JoinRoomUseCase joinRoomUseCase;
    private final SpectateRoomUseCase spectateRoomUseCase;
    private final LeaveRoomUseCase leaveRoomUseCase;
    private final RequestRematchUseCase requestRematchUseCase;
    private final GetRoomUseCase getRoomUseCase;
    private final StartAiPracticeUseCase startAiPracticeUseCase;

    @PostMapping
    public ResponseEntity<ApiResponse<RoomResponse>> createRoom(
            @AuthenticationPrincipal AuthUser userDetails,
            @Valid @RequestBody CreateRoomRequest request) {
        RoomResponse response = createRoomUseCase.createRoom(
                userDetails.id(), request.gameType(), request.omokRule(),
                request.timeLimit(), request.byoyomiOption(), request.ranked());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("방이 생성되었습니다.", response));
    }

    /** 피지컬 오목 'AI 연습' — 봇과 즉시 한 판 시작(레이팅 미반영). 생성된 방으로 입장하면 된다. */
    @PostMapping("/ai-practice")
    public ResponseEntity<ApiResponse<RoomResponse>> startAiPractice(
            @AuthenticationPrincipal AuthUser userDetails) {
        RoomResponse response = startAiPracticeUseCase.startAiPractice(userDetails.id());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("AI 연습 대국을 시작합니다.", response));
    }

    @PostMapping("/{roomCode}/join")
    public ResponseEntity<ApiResponse<RoomResponse>> joinRoom(
            @PathVariable String roomCode,
            @AuthenticationPrincipal AuthUser userDetails) {
        RoomResponse response = joinRoomUseCase.joinRoom(roomCode, userDetails.id());
        return ResponseEntity.ok(ApiResponse.success("방에 참가했습니다.", response));
    }

    @PostMapping("/{roomCode}/spectate")
    public ResponseEntity<ApiResponse<RoomResponse>> spectateRoom(
            @PathVariable String roomCode,
            @AuthenticationPrincipal AuthUser userDetails) {
        RoomResponse response = spectateRoomUseCase.spectateRoom(roomCode, userDetails.id());
        return ResponseEntity.ok(ApiResponse.success("관전을 시작합니다.", response));
    }

    @PostMapping("/{roomCode}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveRoom(
            @PathVariable String roomCode,
            @AuthenticationPrincipal AuthUser userDetails) {
        leaveRoomUseCase.leaveRoom(roomCode, userDetails.id());
        return ResponseEntity.ok(ApiResponse.success("방을 나갔습니다.", null));
    }

    @PostMapping("/{roomCode}/rematch")
    public ResponseEntity<ApiResponse<RoomResponse>> requestRematch(
            @PathVariable String roomCode,
            @AuthenticationPrincipal AuthUser userDetails) {
        RoomResponse response = requestRematchUseCase.requestRematch(roomCode, userDetails.id());
        return ResponseEntity.ok(ApiResponse.success("리매치 요청이 처리되었습니다.", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<RoomResponse>>> getWaitingRooms(
            @AuthenticationPrincipal AuthUser userDetails,
            @RequestParam(required = false) GameType gameType,
            @RequestParam(required = false) Boolean ranked,
            @RequestParam(required = false, defaultValue = "false") boolean recommended,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<RoomResponse> response = recommended
                ? getRoomUseCase.getRecommendedRooms(userDetails.id(), pageable)
                : getRoomUseCase.getWaitingRooms(gameType, ranked, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/live")
    public ResponseEntity<ApiResponse<Page<RoomResponse>>> getInProgressRooms(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<RoomResponse> response = getRoomUseCase.getInProgressRooms(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{roomCode}")
    public ResponseEntity<ApiResponse<RoomResponse>> getRoom(@PathVariable String roomCode) {
        RoomResponse response = getRoomUseCase.getRoom(roomCode);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
