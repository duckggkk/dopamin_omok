package com.dopamin.omok.game.physical.application.port.out;

import com.dopamin.omok.game.physical.application.dto.PhysicalReplayData;

public interface SavePhysicalReplayPort {
    /** 게임당 1회 저장. 이미 존재하면 무시(멱등). */
    void save(PhysicalReplayData replay);
}
