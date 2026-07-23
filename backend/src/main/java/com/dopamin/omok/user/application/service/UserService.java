package com.dopamin.omok.user.application.service;

import com.dopamin.omok.game.domain.GameType;
import com.dopamin.omok.user.application.dto.ModeStats;
import com.dopamin.omok.user.application.dto.RankingMode;
import com.dopamin.omok.user.application.dto.RankingResponse;
import com.dopamin.omok.user.application.dto.PublicUserResponse;
import com.dopamin.omok.user.application.dto.UserResponse;
import com.dopamin.omok.user.application.port.in.GetRankingUseCase;
import com.dopamin.omok.user.application.port.in.GetUserUseCase;
import com.dopamin.omok.user.application.port.in.UpdateProfileUseCase;
import com.dopamin.omok.user.application.port.out.CheckUserExistsPort;
import com.dopamin.omok.user.application.port.out.LoadUserGameStatsPort;
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
    private final LoadUserGameStatsPort loadUserGameStatsPort;

    @Override
    public UserResponse getUser(Long userId) {
        User user = loadUserPort.findById(userId)
                .orElseThrow(() -> new OmokException(ErrorCode.USER_NOT_FOUND));
        return UserResponse.from(user,
                loadUserGameStatsPort.statsByMode(userId, GameType.CLASSIC),
                loadUserGameStatsPort.statsByMode(userId, GameType.PHYSICAL));
    }

    @Override
    public PublicUserResponse getUserByPublicId(UUID publicId, Long viewerUserId) {
        User user = loadUserPort.findByPublicId(publicId)
                .orElseThrow(() -> new OmokException(ErrorCode.USER_NOT_FOUND));
        if (user.isProfilePrivate() && !user.getId().equals(viewerUserId)) {
            throw new OmokException(ErrorCode.PROFILE_PRIVATE);
        }
        return PublicUserResponse.from(user,
                loadUserGameStatsPort.statsByMode(user.getId(), GameType.CLASSIC),
                loadUserGameStatsPort.statsByMode(user.getId(), GameType.PHYSICAL));
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

        return UserResponse.from(user,
                loadUserGameStatsPort.statsByMode(userId, GameType.CLASSIC),
                loadUserGameStatsPort.statsByMode(userId, GameType.PHYSICAL));
    }

    @Override
    public List<RankingResponse> getRanking(int limit, RankingMode mode) {
        int capped = Math.min(Math.max(limit, 1), 100);
        // 탭에 따라 정렬 기준(레이팅)과 표시 전적이 함께 바뀐다.
        List<User> users = switch (mode) {
            case CLASSIC -> loadUserPort.findTopRankedByClassicRating(capped);
            case PHYSICAL -> loadUserPort.findTopRankedByPhysicalRating(capped);
            case TOTAL -> loadUserPort.findTopRanked(capped);
        };
        List<RankingResponse> ranking = new java.util.ArrayList<>(users.size());
        for (int i = 0; i < users.size(); i++) {
            User u = users.get(i);
            ModeStats stats = switch (mode) {
                case CLASSIC -> loadUserGameStatsPort.statsByMode(u.getId(), GameType.CLASSIC);
                case PHYSICAL -> loadUserGameStatsPort.statsByMode(u.getId(), GameType.PHYSICAL);
                // 통합은 users 테이블 누적 컬럼을 그대로 사용(추가 쿼리 없음)
                case TOTAL -> ModeStats.of(u.getWins(), u.getLosses(), u.getDraws());
            };
            ranking.add(RankingResponse.of(i + 1, u, stats));
        }
        return ranking;
    }
}
