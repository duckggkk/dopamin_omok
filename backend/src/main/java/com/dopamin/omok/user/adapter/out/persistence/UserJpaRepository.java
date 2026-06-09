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

    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    @Query("SELECT u FROM User u WHERE (u.wins + u.losses + u.draws) > 0 "
            + "ORDER BY u.wins DESC, u.losses ASC, u.id ASC")
    List<User> findRanked(Pageable pageable);

    /**
     * 잔액이 충분할 때에만 재화를 차감하는 원자적 조건부 UPDATE.
     * 동시 요청에도 DB 행 잠금으로 직렬화되어 lost-update(중복 차감)가 발생하지 않는다.
     * flush/clear 자동화로 호출 직후 같은 트랜잭션에서 최신 잔액을 다시 읽을 수 있다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.currency = u.currency - :amount "
            + "WHERE u.id = :id AND u.currency >= :amount")
    int deductCurrency(@Param("id") Long id, @Param("amount") int amount);
}
