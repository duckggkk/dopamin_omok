package com.dopamin.omok.auth.application.port.in;

public interface RegisterUseCase {
    void register(String email, String password, String nickname);
}
