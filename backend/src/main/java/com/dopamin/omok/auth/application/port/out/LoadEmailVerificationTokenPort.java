package com.dopamin.omok.auth.application.port.out;

import com.dopamin.omok.auth.domain.EmailVerificationToken;

import java.util.Optional;

public interface LoadEmailVerificationTokenPort {
    Optional<EmailVerificationToken> findByToken(String token);
    Optional<EmailVerificationToken> findByUserIdAndToken(Long userId, String token);
    Optional<EmailVerificationToken> findLatestByUserId(Long userId);
}
