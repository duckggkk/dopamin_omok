package com.dopamin.omok.auth.application.port.in;

import com.dopamin.omok.auth.application.dto.TokenResponse;

public interface LoginUseCase {
    TokenResponse login(String email, String password);
}
