package com.dopamin.omok.auth.adapter.out.persistence;

import com.dopamin.omok.auth.domain.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationTokenJpaRepository extends JpaRepository<EmailVerificationToken, Long> {
    Optional<EmailVerificationToken> findByToken(String token);
    Optional<EmailVerificationToken> findByUserIdAndToken(Long userId, String token);
    void deleteByUserId(Long userId);
}
