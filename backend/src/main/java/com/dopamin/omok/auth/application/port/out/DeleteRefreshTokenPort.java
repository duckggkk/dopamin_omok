package com.dopamin.omok.auth.application.port.out;

import com.dopamin.omok.auth.domain.RefreshToken;

public interface DeleteRefreshTokenPort {
    void delete(RefreshToken refreshToken);
    void deleteByUserId(Long userId);
}
