package com.dopamin.omok.game.application.port.in;

import com.dopamin.omok.game.application.dto.RoomResponse;
import com.dopamin.omok.game.domain.ByoyomiOption;
import com.dopamin.omok.game.domain.GameType;
import com.dopamin.omok.game.domain.TimeLimit;

public interface CreateRoomUseCase {
    RoomResponse createRoom(Long userId, GameType gameType, TimeLimit timeLimit, ByoyomiOption byoyomiOption);
}
