package com.dopamin.omok.auth.application.port.out;

import com.dopamin.omok.auth.domain.RefreshToken;

public interface SaveRefreshTokenPort {
    RefreshToken save(RefreshToken refreshToken);
}
