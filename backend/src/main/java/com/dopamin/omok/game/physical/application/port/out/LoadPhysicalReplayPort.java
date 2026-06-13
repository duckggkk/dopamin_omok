package com.dopamin.omok.game.physical.application.port.out;

import com.dopamin.omok.game.physical.application.dto.PhysicalReplayData;

import java.util.Optional;

public interface LoadPhysicalReplayPort {
    Optional<PhysicalReplayData> findByGameId(Long gameId);
}
