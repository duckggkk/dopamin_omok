package com.dopamin.omok.game.application.port.out;

import com.dopamin.omok.game.domain.GameType;
import com.dopamin.omok.game.domain.Room;
import com.dopamin.omok.game.domain.RoomStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LoadRoomPort {
    Optional<Room> findByRoomCode(String roomCode);
    boolean existsByRoomCode(String roomCode);

    /**
     * 이 사용자가 방장으로 열어둔 '살아 있는' 방(WAITING·IN_PROGRESS)을 찾는다.
     * 계정당 활성 방 1개 제약을 검사할 때 쓴다. 폐쇄(CLOSED)된 방은 제외한다.
     */
    Optional<Room> findActiveHostedRoom(Long hostId);

    /**
     * 생성된 지 오래됐는데 아직 WAITING 인 방 목록(정리 스케줄러용).
     * 대국 중(IN_PROGRESS)인 방은 절대 포함하지 않는다.
     */
    List<Room> findStaleWaitingRooms(LocalDateTime createdBefore);
    Page<Room> findByStatus(RoomStatus status, Pageable pageable);
    Page<Room> findByStatusAndGameType(RoomStatus status, GameType gameType, Pageable pageable);
    /** 상태 + (선택)모드 + (선택)랭크/캐주얼 필터로 방을 찾는다. gameType·ranked 가 null 이면 해당 조건 미적용. */
    Page<Room> findRooms(RoomStatus status, GameType gameType, Boolean ranked, Pageable pageable);
    Page<Room> findRecommendedRooms(RoomStatus status, int cmin, int cmax, int pmin, int pmax, Pageable pageable);
}
