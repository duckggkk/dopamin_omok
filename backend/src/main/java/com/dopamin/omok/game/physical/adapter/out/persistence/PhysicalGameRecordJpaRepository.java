package com.dopamin.omok.game.physical.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PhysicalGameRecordJpaRepository extends JpaRepository<PhysicalGameRecordEntity, Long> {
    Optional<PhysicalGameRecordEntity> findByGameId(Long gameId);
    boolean existsByGameId(Long gameId);
}
