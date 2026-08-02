package com.dopamin.omok.game.application.port.out;

import com.dopamin.omok.game.domain.Game;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface LoadGamePort {
    Optional<Game> findById(Long gameId);
    Optional<Game> findActiveGameByRoomCode(String roomCode);
    /** 상태를 바꾸는 경로(착수·기권) 전용 — 행 잠금(SELECT ... FOR UPDATE)까지 건다. */
    Optional<Game> findActiveGameByRoomCodeForUpdate(String roomCode);
    /** 서버 시작 시 고아 피지컬 대국을 정리하기 위한 행 잠금 조회. */
    List<Game> findActivePhysicalGamesForUpdate();
    Optional<Game> findLatestGameByRoomCode(String roomCode);
    Page<Game> findCompletedByUserId(Long userId, Pageable pageable);
}
