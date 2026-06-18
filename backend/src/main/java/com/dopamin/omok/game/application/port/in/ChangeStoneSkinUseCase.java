package com.dopamin.omok.game.application.port.in;

import com.dopamin.omok.game.application.dto.RoomResponse;

public interface ChangeStoneSkinUseCase {
    /** 대기 중인 방에서 참가자가 자신의 바둑알 스킨을 바꾸고, 방 상태를 브로드캐스트한다. */
    RoomResponse changeStoneSkin(String roomCode, Long userId, Long itemId);
}
