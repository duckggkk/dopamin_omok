package com.dopamin.omok.game.application.port.in;

import com.dopamin.omok.game.application.dto.RoomResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GetRoomUseCase {
    RoomResponse getRoom(String roomCode);
    Page<RoomResponse> getWaitingRooms(Pageable pageable);
}
