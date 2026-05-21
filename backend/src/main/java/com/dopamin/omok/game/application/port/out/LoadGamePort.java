package com.dopamin.omok.game.application.port.out;

import com.dopamin.omok.game.domain.Game;
import com.dopamin.omok.game.domain.GameStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface LoadGamePort {
    Optional<Game> findById(Long gameId);
    Optional<Game> findByRoomCode(String roomCode);
    boolean existsByRoomCode(String roomCode);
    Page<Game> findByStatus(GameStatus status, Pageable pageable);
    Page<Game> findByUserId(Long userId, Pageable pageable);
}
