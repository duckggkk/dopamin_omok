package com.dopamin.omok.user.application.service;

import com.dopamin.omok.user.application.dto.RankingResponse;
import com.dopamin.omok.user.application.dto.PublicUserResponse;
import com.dopamin.omok.user.application.dto.UserResponse;
import com.dopamin.omok.user.application.port.in.GetRankingUseCase;
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

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService implements GetUserUseCase, UpdateProfileUseCase, GetRankingUseCase {

    private final LoadUserPort loadUserPort;
    private final CheckUserExistsPort checkUserExistsPort;

    @Override
    public UserResponse getUser(Long userId) {
        User user = loadUserPort.findById(userId)
                .orElseThrow(() -> new OmokException(ErrorCode.USER_NOT_FOUND));
        return UserResponse.from(user);
    }

    @Override
    public PublicUserResponse getUserByPublicId(UUID publicId, Long viewerUserId) {
        User user = loadUserPort.findByPublicId(publicId)
                .orElseThrow(() -> new OmokException(ErrorCode.USER_NOT_FOUND));
        if (user.isProfilePrivate() && !user.getId().equals(viewerUserId)) {
            throw new OmokException(ErrorCode.PROFILE_PRIVATE);
        }
        return PublicUserResponse.from(user);
    }

    @Override
    @Transactional
    public UserResponse updateProfile(Long userId, String nickname, String profileImageUrl, Boolean profilePrivate) {
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
        if (profilePrivate != null) {
            user.updateProfilePrivate(profilePrivate);
        }

        return UserResponse.from(user);
    }

    @Override
    public List<RankingResponse> getRanking(int limit) {
        int capped = Math.min(Math.max(limit, 1), 100);
        List<User> users = loadUserPort.findTopRanked(capped);
        List<RankingResponse> ranking = new java.util.ArrayList<>(users.size());
        for (int i = 0; i < users.size(); i++) {
            ranking.add(RankingResponse.of(i + 1, users.get(i)));
        }
        return ranking;
    }
}
