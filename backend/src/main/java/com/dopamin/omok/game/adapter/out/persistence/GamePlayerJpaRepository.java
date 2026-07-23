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

    /**
     * 아직 닫히지 않은 방(대기·진행 중)에 참가 중인지 여부.
     * 회원 탈퇴 시 상대방이 멈춘 판에 갇히지 않도록 막는 데 쓴다.
     */
    @Query("SELECT COUNT(gp) > 0 FROM GamePlayer gp WHERE gp.user.id = :userId "
            + "AND gp.room.status IN (com.dopamin.omok.game.domain.RoomStatus.WAITING, "
            + "com.dopamin.omok.game.domain.RoomStatus.IN_PROGRESS)")
    boolean existsInActiveRoom(@Param("userId") Long userId);

    void deleteByRoomId(Long roomId);
}
