package com.dopamin.omok.game.adapter.out.persistence;

import com.dopamin.omok.game.application.port.out.LoadGameMovesPort;
import com.dopamin.omok.game.application.port.out.SaveGameMovePort;
import com.dopamin.omok.game.domain.GameMove;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GameMovePersistenceAdapter implements LoadGameMovesPort, SaveGameMovePort {

    private final GameMoveJpaRepository gameMoveJpaRepository;

    @Override
    public List<GameMove> findByGameIdOrderByMoveNumberAsc(Long gameId) {
        return gameMoveJpaRepository.findByGameIdOrderByMoveNumberAsc(gameId);
    }

    @Override
    public GameMove save(GameMove move) {
        return gameMoveJpaRepository.save(move);
    }
}
