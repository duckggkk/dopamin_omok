package com.dopamin.omok.game.adapter.out.persistence;

import com.dopamin.omok.game.domain.Game;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GameJpaRepository extends JpaRepository<Game, Long> {

    @Query("SELECT g FROM Game g WHERE g.room.roomCode = :roomCode AND g.status = com.dopamin.omok.game.domain.GameStatus.IN_PROGRESS")
    Optional<Game> findActiveGameByRoomCode(@Param("roomCode") String roomCode);

    /**
     * 착수·기권처럼 <b>게임 상태를 바꾸는</b> 경로 전용 조회.
     * <p>
     * {@code PESSIMISTIC_WRITE} 는 {@code SELECT ... FOR UPDATE} 로 나가서 해당 games 행을 잠근다.
     * 착수 로직은 "기존 수순 조회 → 중복/턴 검사 → moveNumber 계산 → 저장" 순인데,
     * 이 구간에 락이 없으면 같은 게임에 대한 동시 요청 두 건이 <b>같은 스냅샷</b>을 읽어
     * 둘 다 검사를 통과하고 같은 moveNumber 로 저장된다
     * (한 턴에 돌 2개 · 기보 깨짐 · 승패 이중 반영).
     * <p>
     * 조회 전용 경로({@link #findActiveGameByRoomCode})는 락 없이 그대로 둔다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM Game g WHERE g.room.roomCode = :roomCode AND g.status = com.dopamin.omok.game.domain.GameStatus.IN_PROGRESS")
    Optional<Game> findActiveGameByRoomCodeForUpdate(@Param("roomCode") String roomCode);

    @Query("SELECT g FROM Game g WHERE g.room.roomCode = :roomCode ORDER BY g.gameNumber DESC LIMIT 1")
    Optional<Game> findLatestGameByRoomCode(@Param("roomCode") String roomCode);

    // AI 연습(봇 대국)은 기보/리플레이 다시보기에서 제외한다 — 어느 한쪽이라도 BOT 이면 빼낸다(레이팅·랭킹과 동일 취급).
    //
    // 플레이어는 명시적 LEFT JOIN 으로 붙인다. 상대가 하드 삭제되면(게스트 정리, V38 SET NULL)
    // black/white 가 NULL 이 되는데, 암묵 경로(g.blackPlayer.role)는 INNER JOIN 으로 번역돼
    // 그 대국이 결과에서 통째로 사라진다 — 내 기보 목록에서 지워진 게스트와의 판이 증발하는 버그.
    // NULL 플레이어는 봇이 아니므로(봇 계정은 삭제되지 않는다) 목록에 남긴다.
    @Query("SELECT g FROM Game g " +
           "LEFT JOIN g.blackPlayer bp LEFT JOIN g.whitePlayer wp " +
           "WHERE (bp.id = :userId OR wp.id = :userId) " +
           "AND g.status IN (com.dopamin.omok.game.domain.GameStatus.FINISHED, " +
           "com.dopamin.omok.game.domain.GameStatus.DRAW, " +
           "com.dopamin.omok.game.domain.GameStatus.ABANDONED) " +
           "AND (bp IS NULL OR bp.role <> com.dopamin.omok.user.domain.UserRole.BOT) " +
           "AND (wp IS NULL OR wp.role <> com.dopamin.omok.user.domain.UserRole.BOT) " +
           "ORDER BY g.createdAt DESC")
    Page<Game> findCompletedByUserId(@Param("userId") Long userId, Pageable pageable);
}
