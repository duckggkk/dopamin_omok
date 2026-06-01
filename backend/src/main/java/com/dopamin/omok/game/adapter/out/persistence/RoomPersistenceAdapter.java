package com.dopamin.omok.game.adapter.out.persistence;

import com.dopamin.omok.game.application.port.out.LoadRoomPort;
import com.dopamin.omok.game.application.port.out.SaveRoomPort;
import com.dopamin.omok.game.domain.Room;
import com.dopamin.omok.game.domain.RoomStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RoomPersistenceAdapter implements LoadRoomPort, SaveRoomPort {

    private final RoomJpaRepository roomJpaRepository;

    @Override
    public Optional<Room> findByRoomCode(String roomCode) {
        return roomJpaRepository.findByRoomCode(roomCode);
    }

    @Override
    public boolean existsByRoomCode(String roomCode) {
        return roomJpaRepository.existsByRoomCode(roomCode);
    }

    @Override
    public Page<Room> findByStatus(RoomStatus status, Pageable pageable) {
        return roomJpaRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    @Override
    public Room save(Room room) {
        return roomJpaRepository.save(room);
    }
}
