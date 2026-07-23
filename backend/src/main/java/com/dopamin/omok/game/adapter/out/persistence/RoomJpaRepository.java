package com.dopamin.omok.game.adapter.out.persistence;

import com.dopamin.omok.game.domain.Room;
import com.dopamin.omok.game.domain.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoomJpaRepository extends JpaRepository<Room, Long>, RoomRepositoryCustom {
    Optional<Room> findByRoomCode(String roomCode);
    boolean existsByRoomCode(String roomCode);

    /**
     * 방장이 열어둔 살아 있는 방 1건. idx_rooms_host 인덱스를 탄다.
     * 이론상 활성 방은 최대 1개지만, 제약 도입 이전에 만들어진 데이터가 있을 수 있으므로
     * findFirst + 최신순으로 안전하게 1건만 가져온다.
     */
    Optional<Room> findFirstByHostIdAndStatusInOrderByIdDesc(Long hostId, Collection<RoomStatus> statuses);

    /** WAITING 상태로 오래 방치된 방. idx_rooms_status 인덱스를 탄다. */
    List<Room> findByStatusAndCreatedAtBefore(RoomStatus status, LocalDateTime createdBefore);
}
