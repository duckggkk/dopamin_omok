package com.dopamin.omok.auth.application.port.in;

import com.dopamin.omok.auth.application.dto.TokenResponse;

public interface RegisterUseCase {
    TokenResponse register(String email, String password, String nickname);
}
