package com.dopamin.omok.game.adapter.out.persistence;

import com.dopamin.omok.game.domain.Game;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GameJpaRepository extends JpaRepository<Game, Long> {

    @Query("SELECT g FROM Game g WHERE g.room.roomCode = :roomCode AND g.status = com.dopamin.omok.game.domain.GameStatus.IN_PROGRESS")
    Optional<Game> findActiveGameByRoomCode(@Param("roomCode") String roomCode);

    @Query("SELECT g FROM Game g WHERE g.room.roomCode = :roomCode ORDER BY g.gameNumber DESC LIMIT 1")
    Optional<Game> findLatestGameByRoomCode(@Param("roomCode") String roomCode);

    // AI 연습(봇 대국)은 기보/리플레이 다시보기에서 제외한다 — 어느 한쪽이라도 BOT 이면 빼낸다(레이팅·랭킹과 동일 취급).
    // 완성된 대국은 흑·백이 항상 채워져 있으므로 role 비교만으로 충분하다.
    @Query("SELECT g FROM Game g WHERE (g.blackPlayer.id = :userId OR g.whitePlayer.id = :userId) " +
           "AND g.status IN (com.dopamin.omok.game.domain.GameStatus.FINISHED, " +
           "com.dopamin.omok.game.domain.GameStatus.DRAW, " +
           "com.dopamin.omok.game.domain.GameStatus.ABANDONED) " +
           "AND g.blackPlayer.role <> com.dopamin.omok.user.domain.UserRole.BOT " +
           "AND g.whitePlayer.role <> com.dopamin.omok.user.domain.UserRole.BOT " +
           "ORDER BY g.createdAt DESC")
    Page<Game> findCompletedByUserId(@Param("userId") Long userId, Pageable pageable);
}
