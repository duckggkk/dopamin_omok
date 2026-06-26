package com.dopamin.omok.game.application.port.out;

import com.dopamin.omok.game.domain.GameType;
import com.dopamin.omok.game.domain.Room;
import com.dopamin.omok.game.domain.RoomStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface LoadRoomPort {
    Optional<Room> findByRoomCode(String roomCode);
    boolean existsByRoomCode(String roomCode);
    Page<Room> findByStatus(RoomStatus status, Pageable pageable);
    Page<Room> findByStatusAndGameType(RoomStatus status, GameType gameType, Pageable pageable);
    /** 상태 + (선택)모드 + (선택)랭크/캐주얼 필터로 방을 찾는다. gameType·ranked 가 null 이면 해당 조건 미적용. */
    Page<Room> findRooms(RoomStatus status, GameType gameType, Boolean ranked, Pageable pageable);
    Page<Room> findRecommendedRooms(RoomStatus status, int cmin, int cmax, int pmin, int pmax, Pageable pageable);
}
