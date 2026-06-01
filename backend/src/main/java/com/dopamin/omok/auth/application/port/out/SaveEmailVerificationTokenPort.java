package com.dopamin.omok.auth.application.port.out;

import com.dopamin.omok.auth.domain.EmailVerificationToken;

public interface SaveEmailVerificationTokenPort {
    EmailVerificationToken save(EmailVerificationToken token);
}
