package com.dopamin.omok.game.adapter.out.persistence;

import com.dopamin.omok.game.domain.Game;
import com.dopamin.omok.game.domain.GameStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GameJpaRepository extends JpaRepository<Game, Long> {

    Optional<Game> findByRoomCode(String roomCode);

    Page<Game> findByStatusOrderByCreatedAtDesc(GameStatus status, Pageable pageable);

    @Query("SELECT g FROM Game g WHERE (g.blackPlayer.id = :userId OR g.whitePlayer.id = :userId) ORDER BY g.createdAt DESC")
    Page<Game> findByUserId(@Param("userId") Long userId, Pageable pageable);

    boolean existsByRoomCode(String roomCode);
}
