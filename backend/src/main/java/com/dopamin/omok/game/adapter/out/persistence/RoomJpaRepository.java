package com.dopamin.omok.game.adapter.out.persistence;

import com.dopamin.omok.game.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoomJpaRepository extends JpaRepository<Room, Long>, RoomRepositoryCustom {
    Optional<Room> findByRoomCode(String roomCode);
    boolean existsByRoomCode(String roomCode);
}
