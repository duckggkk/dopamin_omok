package com.dopamin.omok.game.adapter.out.persistence;

import com.dopamin.omok.game.domain.Room;
import com.dopamin.omok.game.domain.RoomStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoomJpaRepository extends JpaRepository<Room, Long> {
    Optional<Room> findByRoomCode(String roomCode);
    boolean existsByRoomCode(String roomCode);
    Page<Room> findByStatusOrderByCreatedAtDesc(RoomStatus status, Pageable pageable);
}
