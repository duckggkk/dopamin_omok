package com.dopamin.omok.user.application.service;

import com.dopamin.omok.user.application.dto.UserResponse;
import com.dopamin.omok.user.application.port.in.GetUserUseCase;
import com.dopamin.omok.user.application.port.in.UpdateProfileUseCase;
import com.dopamin.omok.user.application.port.out.CheckUserExistsPort;
import com.dopamin.omok.user.application.port.out.LoadUserPort;
import com.dopamin.omok.user.domain.User;
import com.dopamin.omok.global.common.exception.ErrorCode;
import com.dopamin.omok.global.common.exception.OmokException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService implements GetUserUseCase, UpdateProfileUseCase {

    private final LoadUserPort loadUserPort;
    private final CheckUserExistsPort checkUserExistsPort;

    @Override
    public UserResponse getUser(Long userId) {
        User user = loadUserPort.findById(userId)
                .orElseThrow(() -> new OmokException(ErrorCode.USER_NOT_FOUND));
        return UserResponse.from(user);
    }

    @Override
    public UserResponse getUserByPublicId(UUID publicId) {
        User user = loadUserPort.findByPublicId(publicId)
                .orElseThrow(() -> new OmokException(ErrorCode.USER_NOT_FOUND));
        return UserResponse.from(user);
    }

    @Override
    @Transactional
    public UserResponse updateProfile(Long userId, String nickname, String profileImageUrl) {
        User user = loadUserPort.findById(userId)
                .orElseThrow(() -> new OmokException(ErrorCode.USER_NOT_FOUND));

        if (StringUtils.hasText(nickname) && !nickname.equals(user.getNickname())) {
            if (checkUserExistsPort.existsByNickname(nickname)) {
                throw new OmokException(ErrorCode.NICKNAME_ALREADY_EXISTS);
            }
            user.updateNickname(nickname);
        }

        if (profileImageUrl != null) {
            user.updateProfileImageUrl(profileImageUrl);
        }

        return UserResponse.from(user);
    }
}
