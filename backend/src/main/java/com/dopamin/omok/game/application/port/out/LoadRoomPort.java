package com.dopamin.omok.game.application.port.out;

import com.dopamin.omok.game.domain.Room;
import com.dopamin.omok.game.domain.RoomStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface LoadRoomPort {
    Optional<Room> findByRoomCode(String roomCode);
    boolean existsByRoomCode(String roomCode);
    Page<Room> findByStatus(RoomStatus status, Pageable pageable);
}
