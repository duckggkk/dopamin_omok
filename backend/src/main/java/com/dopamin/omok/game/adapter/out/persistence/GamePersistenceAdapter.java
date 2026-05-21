package com.dopamin.omok.game.adapter.out.persistence;

import com.dopamin.omok.game.application.port.out.LoadGamePort;
import com.dopamin.omok.game.application.port.out.SaveGamePort;
import com.dopamin.omok.game.domain.Game;
import com.dopamin.omok.game.domain.GameStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class GamePersistenceAdapter implements LoadGamePort, SaveGamePort {

    private final GameJpaRepository gameJpaRepository;

    @Override
    public Optional<Game> findById(Long gameId) {
        return gameJpaRepository.findById(gameId);
    }

    @Override
    public Optional<Game> findByRoomCode(String roomCode) {
        return gameJpaRepository.findByRoomCode(roomCode);
    }

    @Override
    public boolean existsByRoomCode(String roomCode) {
        return gameJpaRepository.existsByRoomCode(roomCode);
    }

    @Override
    public Page<Game> findByStatus(GameStatus status, Pageable pageable) {
        return gameJpaRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    @Override
    public Page<Game> findByUserId(Long userId, Pageable pageable) {
        return gameJpaRepository.findByUserId(userId, pageable);
    }

    @Override
    public Game save(Game game) {
        return gameJpaRepository.save(game);
    }
}
