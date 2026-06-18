package com.dopamin.omok.game.application.port.out;

import com.dopamin.omok.game.application.dto.RoomResponse;

public interface RoomEventPublisherPort {
    void publishStatus(String roomCode, RoomResponse response);
    void publishClosed(String roomCode, String message);
    void publishNotice(String roomCode, String message);
}
