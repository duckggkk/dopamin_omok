package com.dopamin.omok.user.adapter.out.persistence;

import com.dopamin.omok.user.domain.AuthProvider;
import com.dopamin.omok.user.domain.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * <b>탈퇴(soft delete) 취급 규칙 — 여기가 유일한 진입점이다.</b>
 * <ul>
 *   <li>{@code findActiveXxx} : 탈퇴 행을 <b>제외</b>한다. "이 사용자로 무언가를 한다"(로그인·프로필·
 *       친구 찾기·방 참가 …)는 모든 경로가 이쪽을 쓴다.</li>
 *   <li>{@code existsByXxx} : 탈퇴 행을 <b>포함</b>한다. 가입/닉네임 변경의 중복 검사 전용으로,
 *       익명화된 닉네임({@code 탈퇴한사용자_<id>})을 다른 사람이 가져가 UNIQUE 제약을 깨거나
 *       과거 기보의 탈퇴자를 사칭하는 것을 막는다.</li>
 * </ul>
 * 새 조회를 추가할 때는 둘 중 어느 쪽인지 먼저 정할 것.
 */
public interface UserJpaRepository extends JpaRepository<User, Long> {

    /** 탈퇴하지 않은(활성) 사용자만 대상으로 하는 공통 조건. */
    String ACTIVE = "u.deletedAt IS NULL ";

    @Query("SELECT u FROM User u WHERE u.id = :id AND " + ACTIVE)
    Optional<User> findActiveById(@Param("id") Long id);

    @Query("SELECT u FROM User u WHERE u.publicId = :publicId AND " + ACTIVE)
    Optional<User> findActiveByPublicId(@Param("publicId") UUID publicId);

    @Query("SELECT u FROM User u WHERE u.email = :email AND " + ACTIVE)
    Optional<User> findActiveByEmail(@Param("email") String email);

    @Query("SELECT u FROM User u WHERE u.nickname = :nickname AND " + ACTIVE)
    Optional<User> findActiveByNickname(@Param("nickname") String nickname);

    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);

    /** 중복 검사 전용 — 탈퇴 행도 '사용 중'으로 본다(위 클래스 주석 참고). */
    boolean existsByEmail(String email);

    /** 중복 검사 전용 — 탈퇴 행도 '사용 중'으로 본다(위 클래스 주석 참고). */
    boolean existsByNickname(String nickname);

    /**
     * 랭킹에 오를 자격 — 한 판이라도 둔 회원. 게스트(익명)와 AI 봇 계정은 순위에서 제외한다.
     * 대국의 '랭크/캐주얼' 표시는 보지 않는다(모든 회원 대국이 레이팅에 반영되므로).
     * 탈퇴 회원도 제외한다 — 익명화된 닉네임이 순위표에 노출되면 안 된다.
     */
    String RANKABLE = "(u.wins + u.losses + u.draws) > 0 "
            + "AND u.deletedAt IS NULL "
            + "AND u.role <> com.dopamin.omok.user.domain.UserRole.GUEST "
            + "AND u.role <> com.dopamin.omok.user.domain.UserRole.BOT ";

    @Query("SELECT u FROM User u WHERE " + RANKABLE
            + "ORDER BY u.wins DESC, u.losses ASC, u.id ASC")
    List<User> findRanked(Pageable pageable);

    @Query("SELECT u FROM User u WHERE " + RANKABLE
            + "ORDER BY u.classicRating DESC, u.id ASC")
    List<User> findRankedByClassicRating(Pageable pageable);

    @Query("SELECT u FROM User u WHERE " + RANKABLE
            + "ORDER BY u.physicalRating DESC, u.id ASC")
    List<User> findRankedByPhysicalRating(Pageable pageable);

    /**
     * 잔액이 충분할 때에만 재화를 차감하는 원자적 조건부 UPDATE.
     * 동시 요청에도 DB 행 잠금으로 직렬화되어 lost-update(중복 차감)가 발생하지 않는다.
     * flush/clear 자동화로 호출 직후 같은 트랜잭션에서 최신 잔액을 다시 읽을 수 있다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.currency = u.currency - :amount "
            + "WHERE u.id = :id AND u.currency >= :amount")
    int deductCurrency(@Param("id") Long id, @Param("amount") int amount);

    /**
     * 사용자를 즉시 삭제한다. 벌크 DELETE 라 호출 시점에 SQL이 바로 실행되어
     * 이후의 신규 가입 INSERT보다 먼저 처리된다(같은 이메일/닉네임 유니크 충돌 방지).
     * 연관 행(user_items·user_active_items·email_verification_tokens)은
     * DB의 ON DELETE CASCADE 로 함께 삭제된다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM User u WHERE u.id = :id")
    void deleteByIdImmediately(@Param("id") Long id);

    /**
     * cutoff 이전에 생성된 게스트 계정을 일괄 삭제한다(쌓이는 익명 계정 청소).
     * <p>
     * 대국 기록은 남는다 — rooms.host_id·games.black/white_player_id·game_moves.player_id 가
     * 모두 ON DELETE SET NULL(V1·V38)이라 방·대국·기보 행은 그대로 두고 사람 칸만 비워진다.
     * game_players·보유 아이템만 CASCADE 로 함께 삭제된다.
     * <p>
     * <b>활성 방(대기·진행 중)에 참가 중인 게스트는 건너뛴다.</b> 생성 7일이 지나도 아직
     * 접속해 게임 중일 수 있는데(정리는 생성 시각 기준), 지우면 진행 중인 판의 방장/상대가
     * 증발한다. game_players 행은 방이 닫힐 때 함께 지워지므로(RoomService.deleteByRoomId)
     * "game_players 행이 있다 = 살아 있는 방에 있다"로 판정할 수 있다. 건너뛴 게스트는
     * 방이 닫힌 뒤 다음 날 새벽 정리에서 삭제된다.
     * 이 가드 덕분에 살아 있는 방의 방장·참가자는 항상 존재한다(라이브 경로 null 불변식).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM User u WHERE u.role = com.dopamin.omok.user.domain.UserRole.GUEST "
            + "AND u.createdAt < :cutoff "
            + "AND NOT EXISTS (SELECT 1 FROM GamePlayer gp WHERE gp.user = u)")
    int deleteGuestsCreatedBefore(@Param("cutoff") java.time.LocalDateTime cutoff);
}
