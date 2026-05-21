package com.dopamin.omok.user.application.port.out;

import com.dopamin.omok.user.domain.User;

import java.util.Optional;
import java.util.UUID;

public interface LoadUserPort {
    Optional<User> findById(Long userId);
    Optional<User> findByEmail(String email);
    Optional<User> findByPublicId(UUID publicId);
}
