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

public interface UserJpaRepository extends JpaRepository<User, Long> {

    Optional<User> findByPublicId(UUID publicId);

    Optional<User> findByEmail(String email);

    Optional<User> findByNickname(String nickname);

    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    @Query("SELECT u FROM User u WHERE (u.wins + u.losses + u.draws) > 0 "
            + "ORDER BY u.wins DESC, u.losses ASC, u.id ASC")
    List<User> findRanked(Pageable pageable);

    @Query("SELECT u FROM User u WHERE (u.wins + u.losses + u.draws) > 0 "
            + "ORDER BY u.classicRating DESC, u.id ASC")
    List<User> findRankedByClassicRating(Pageable pageable);

    @Query("SELECT u FROM User u WHERE (u.wins + u.losses + u.draws) > 0 "
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
     * DB FK 동작(games SET NULL, game_moves·game_players·아이템 CASCADE)이 그대로 적용된다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM User u WHERE u.role = com.dopamin.omok.user.domain.UserRole.GUEST "
            + "AND u.createdAt < :cutoff")
    int deleteGuestsCreatedBefore(@Param("cutoff") java.time.LocalDateTime cutoff);
}
