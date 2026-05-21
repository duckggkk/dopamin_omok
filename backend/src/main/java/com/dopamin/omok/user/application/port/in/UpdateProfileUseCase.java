package com.dopamin.omok.user.application.port.in;

import com.dopamin.omok.user.application.dto.UserResponse;

public interface UpdateProfileUseCase {
    UserResponse updateProfile(Long userId, String nickname, String profileImageUrl);
}
