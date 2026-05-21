package com.dopamin.omok.auth.application.port.out;

import com.dopamin.omok.auth.domain.RefreshToken;

import java.util.Optional;

public interface LoadRefreshTokenPort {
    Optional<RefreshToken> findByToken(String token);
}
