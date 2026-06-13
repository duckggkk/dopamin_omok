package com.dopamin.omok.game.adapter.out.persistence;

import com.dopamin.omok.game.domain.GameType;
import com.dopamin.omok.game.domain.Room;
import com.dopamin.omok.game.domain.RoomStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RoomJpaRepository extends JpaRepository<Room, Long> {
    Optional<Room> findByRoomCode(String roomCode);
    boolean existsByRoomCode(String roomCode);
    Page<Room> findByStatusOrderByCreatedAtDesc(RoomStatus status, Pageable pageable);
    Page<Room> findByStatusAndGameTypeOrderByCreatedAtDesc(RoomStatus status, GameType gameType, Pageable pageable);

    /**
     * '내 레이팅대 추천' 필터용. 방을 그 방의 모드에 맞는 방장 레이팅으로 비교한다.
     * 일반 방은 방장 일반 레이팅이 [cmin, cmax], 피지컬 방은 방장 피지컬 레이팅이 [pmin, pmax] 구간에 들면 추천.
     */
    @Query("SELECT r FROM Room r WHERE r.status = :status AND ("
            + "(r.gameType = com.dopamin.omok.game.domain.GameType.CLASSIC AND r.host.classicRating BETWEEN :cmin AND :cmax) OR "
            + "(r.gameType = com.dopamin.omok.game.domain.GameType.PHYSICAL AND r.host.physicalRating BETWEEN :pmin AND :pmax)) "
            + "ORDER BY r.createdAt DESC")
    Page<Room> findRecommendedRooms(@Param("status") RoomStatus status,
                                    @Param("cmin") int cmin, @Param("cmax") int cmax,
                                    @Param("pmin") int pmin, @Param("pmax") int pmax,
                                    Pageable pageable);
}
