package com.dopamin.omok.game.application.port.out;

import com.dopamin.omok.game.domain.GamePlayer;
import com.dopamin.omok.game.domain.PlayerRole;

import java.util.List;
import java.util.Optional;

public interface LoadGamePlayerPort {
    Optional<GamePlayer> findByRoomIdAndUserId(Long roomId, Long userId);
    List<GamePlayer> findByRoomId(Long roomId);
    List<GamePlayer> findByRoomIdAndRole(Long roomId, PlayerRole role);
    int countSpectatorsByRoomId(Long roomId);
}
