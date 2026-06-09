package com.dopamin.omok.user.adapter.out.persistence;

import com.dopamin.omok.user.domain.AuthProvider;
import com.dopamin.omok.user.domain.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
}
