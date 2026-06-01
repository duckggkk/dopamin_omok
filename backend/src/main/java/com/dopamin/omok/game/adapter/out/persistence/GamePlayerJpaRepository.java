package com.dopamin.omok.game.adapter.out.persistence;

import com.dopamin.omok.game.domain.GamePlayer;
import com.dopamin.omok.game.domain.PlayerRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GamePlayerJpaRepository extends JpaRepository<GamePlayer, Long> {
    Optional<GamePlayer> findByRoomIdAndUserId(Long roomId, Long userId);
    List<GamePlayer> findByRoomId(Long roomId);
    List<GamePlayer> findByRoomIdAndRole(Long roomId, PlayerRole role);

    @Query("SELECT COUNT(gp) FROM GamePlayer gp WHERE gp.room.id = :roomId AND gp.role = 'SPECTATOR'")
    int countSpectatorsByRoomId(@Param("roomId") Long roomId);

    void deleteByRoomId(Long roomId);
}
