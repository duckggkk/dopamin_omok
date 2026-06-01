package com.dopamin.omok.auth.application.port.out;

import com.dopamin.omok.auth.domain.EmailVerificationToken;

public interface DeleteEmailVerificationTokenPort {
    void delete(EmailVerificationToken token);
    void deleteByUserId(Long userId);
}
