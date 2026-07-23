package com.dopamin.omok.game.adapter.out.persistence;

import com.dopamin.omok.game.application.port.out.LoadRoomPort;
import com.dopamin.omok.game.application.port.out.SaveRoomPort;
import com.dopamin.omok.game.domain.GameType;
import com.dopamin.omok.game.domain.Room;
import com.dopamin.omok.game.domain.RoomStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RoomPersistenceAdapter implements LoadRoomPort, SaveRoomPort {

    /** '살아 있는 방'의 정의 — 아직 닫히지 않은 상태. */
    private static final List<RoomStatus> ACTIVE_STATUSES =
            List.of(RoomStatus.WAITING, RoomStatus.IN_PROGRESS);

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
    public Optional<Room> findActiveHostedRoom(Long hostId) {
        return roomJpaRepository.findFirstByHostIdAndStatusInOrderByIdDesc(hostId, ACTIVE_STATUSES);
    }

    @Override
    public List<Room> findStaleWaitingRooms(LocalDateTime createdBefore) {
        return roomJpaRepository.findByStatusAndCreatedAtBefore(RoomStatus.WAITING, createdBefore);
    }

    @Override
    public Page<Room> findByStatus(RoomStatus status, Pageable pageable) {
        return roomJpaRepository.search(RoomSearchCondition.of(status), pageable);
    }

    @Override
    public Page<Room> findByStatusAndGameType(RoomStatus status, GameType gameType, Pageable pageable) {
        return roomJpaRepository.search(RoomSearchCondition.of(status, gameType), pageable);
    }

    @Override
    public Page<Room> findRooms(RoomStatus status, GameType gameType, Boolean ranked, Pageable pageable) {
        return roomJpaRepository.search(RoomSearchCondition.waiting(status, gameType, ranked), pageable);
    }

    @Override
    public Page<Room> findRecommendedRooms(RoomStatus status, int cmin, int cmax, int pmin, int pmax, Pageable pageable) {
        return roomJpaRepository.search(
                RoomSearchCondition.recommended(status,
                        new RoomSearchCondition.RatingBand(cmin, cmax, pmin, pmax)),
                pageable);
    }

    @Override
    public Room save(Room room) {
        return roomJpaRepository.save(room);
    }
}
