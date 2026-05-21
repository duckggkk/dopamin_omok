package com.dopamin.omok.user.application.port.out;

import com.dopamin.omok.user.domain.User;

public interface SaveUserPort {
    User save(User user);
}
