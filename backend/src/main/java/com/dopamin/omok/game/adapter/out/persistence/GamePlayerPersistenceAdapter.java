package com.dopamin.omok.game.adapter.out.persistence;

import com.dopamin.omok.game.application.port.out.DeleteGamePlayerPort;
import com.dopamin.omok.game.application.port.out.LoadGamePlayerPort;
import com.dopamin.omok.game.application.port.out.SaveGamePlayerPort;
import com.dopamin.omok.game.domain.GamePlayer;
import com.dopamin.omok.game.domain.PlayerRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class GamePlayerPersistenceAdapter implements LoadGamePlayerPort, SaveGamePlayerPort, DeleteGamePlayerPort {

    private final GamePlayerJpaRepository gamePlayerJpaRepository;

    @Override
    public Optional<GamePlayer> findByRoomIdAndUserId(Long roomId, Long userId) {
        return gamePlayerJpaRepository.findByRoomIdAndUserId(roomId, userId);
    }

    @Override
    public List<GamePlayer> findByRoomId(Long roomId) {
        return gamePlayerJpaRepository.findByRoomId(roomId);
    }

    @Override
    public List<GamePlayer> findByRoomIdAndRole(Long roomId, PlayerRole role) {
        return gamePlayerJpaRepository.findByRoomIdAndRole(roomId, role);
    }

    @Override
    public int countSpectatorsByRoomId(Long roomId) {
        return gamePlayerJpaRepository.countSpectatorsByRoomId(roomId);
    }

    @Override
    public GamePlayer save(GamePlayer gamePlayer) {
        return gamePlayerJpaRepository.save(gamePlayer);
    }

    @Override
    public void delete(GamePlayer gamePlayer) {
        gamePlayerJpaRepository.delete(gamePlayer);
    }

    @Override
    public void deleteByRoomId(Long roomId) {
        gamePlayerJpaRepository.deleteByRoomId(roomId);
    }
}
