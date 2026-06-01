package com.dopamin.omok.game.application.port.in;

import com.dopamin.omok.game.application.dto.RoomResponse;

public interface RequestRematchUseCase {
    RoomResponse requestRematch(String roomCode, Long userId);
}
