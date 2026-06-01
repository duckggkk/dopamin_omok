package com.dopamin.omok.auth.application.port.in;

public interface VerifyEmailUseCase {
    void verifyEmail(String email, String code);
}
