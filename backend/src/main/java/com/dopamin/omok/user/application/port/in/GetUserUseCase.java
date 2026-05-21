package com.dopamin.omok.user.application.port.in;

import com.dopamin.omok.user.application.dto.UserResponse;

import java.util.UUID;

public interface GetUserUseCase {
    UserResponse getUser(Long userId);
    UserResponse getUserByPublicId(UUID publicId);
}
