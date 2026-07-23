package com.dopamin.omok.game.adapter.out.persistence;

import com.dopamin.omok.game.application.port.out.LoadGamePort;
import com.dopamin.omok.game.application.port.out.SaveGamePort;
import com.dopamin.omok.game.domain.Game;
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
    public Optional<Game> findActiveGameByRoomCode(String roomCode) {
        return gameJpaRepository.findActiveGameByRoomCode(roomCode);
    }

    @Override
    public Optional<Game> findActiveGameByRoomCodeForUpdate(String roomCode) {
        return gameJpaRepository.findActiveGameByRoomCodeForUpdate(roomCode);
    }

    @Override
    public Optional<Game> findLatestGameByRoomCode(String roomCode) {
        return gameJpaRepository.findLatestGameByRoomCode(roomCode);
    }

    @Override
    public Page<Game> findCompletedByUserId(Long userId, Pageable pageable) {
        return gameJpaRepository.findCompletedByUserId(userId, pageable);
    }

    @Override
    public Game save(Game game) {
        return gameJpaRepository.save(game);
    }
}
