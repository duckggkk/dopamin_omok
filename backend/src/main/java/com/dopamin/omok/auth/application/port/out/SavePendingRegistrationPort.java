package com.dopamin.omok.auth.application.port.out;

import com.dopamin.omok.auth.domain.PendingRegistration;

public interface SavePendingRegistrationPort {
    PendingRegistration save(PendingRegistration pending);
}
