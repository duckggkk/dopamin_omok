package com.dopamin.omok.game.application.port.in;

import com.dopamin.omok.game.application.dto.RoomResponse;
import com.dopamin.omok.game.domain.GameType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GetRoomUseCase {
    RoomResponse getRoom(String roomCode);
    /** 대기 중(WAITING) 방 목록. gameType·ranked 가 null이면 해당 필터 미적용. */
    Page<RoomResponse> getWaitingRooms(GameType gameType, Boolean ranked, Pageable pageable);
    /** 방장 레이팅이 내 레이팅과 비슷한(±밴드) 대기 중 방만 추천. */
    Page<RoomResponse> getRecommendedRooms(Long userId, Pageable pageable);
    /** 진행 중(IN_PROGRESS)인 방 목록 — 관전용. */
    Page<RoomResponse> getInProgressRooms(Pageable pageable);
}
