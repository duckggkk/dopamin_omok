package com.dopamin.omok.game.adapter.out.persistence;

import com.dopamin.omok.game.domain.GameMove;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GameMoveJpaRepository extends JpaRepository<GameMove, Long> {

    List<GameMove> findByGameIdOrderByMoveNumberAsc(Long gameId);

    @Query("SELECT COUNT(m) FROM GameMove m WHERE m.game.id = :gameId")
    int countByGameId(@Param("gameId") Long gameId);
}
