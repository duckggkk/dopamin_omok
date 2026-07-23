package com.dopamin.omok.game.application.port.in;

import com.dopamin.omok.game.application.dto.RoomResponse;

public interface SwapColorsUseCase {
    /** 대기 중인 방에서 방장이 두 참가자의 흑/백을 맞바꾸고, 방 상태를 브로드캐스트한다. */
    RoomResponse swapColors(String roomCode, Long userId);
}
