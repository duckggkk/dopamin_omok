package com.dopamin.omok.game.physical.adapter.out.persistence;

import com.dopamin.omok.game.physical.application.dto.PhysicalReplayData;
import com.dopamin.omok.game.physical.application.port.out.LoadPhysicalReplayPort;
import com.dopamin.omok.game.physical.application.port.out.SavePhysicalReplayPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PhysicalReplayPersistenceAdapter implements SavePhysicalReplayPort, LoadPhysicalReplayPort {

    private final PhysicalGameRecordJpaRepository repository;

    @Override
    public void save(PhysicalReplayData replay) {
        if (replay == null || replay.gameId() == null) return;
        if (repository.existsByGameId(replay.gameId())) return; // 멱등 — 게임당 1행
        repository.save(new PhysicalGameRecordEntity(replay.gameId(), replay));
    }

    @Override
    public Optional<PhysicalReplayData> findByGameId(Long gameId) {
        return repository.findByGameId(gameId).map(PhysicalGameRecordEntity::getReplay);
    }
}
