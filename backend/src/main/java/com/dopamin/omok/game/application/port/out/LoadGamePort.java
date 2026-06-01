package com.dopamin.omok.game.application.port.out;

import com.dopamin.omok.game.domain.Game;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface LoadGamePort {
    Optional<Game> findById(Long gameId);
    Optional<Game> findActiveGameByRoomCode(String roomCode);
    Optional<Game> findLatestGameByRoomCode(String roomCode);
    Page<Game> findCompletedByUserId(Long userId, Pageable pageable);
}
