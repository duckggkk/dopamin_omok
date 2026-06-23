package com.dopamin.omok.user.application.port.out;

import com.dopamin.omok.user.domain.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoadUserPort {
    Optional<User> findById(Long userId);
    Optional<User> findByEmail(String email);
    Optional<User> findByNickname(String nickname);
    Optional<User> findByPublicId(UUID publicId);

    /** 통합 랭킹: 한 판이라도 둔 사용자 중 승수↓·패수↑ 순으로 상위 limit명. */
    List<User> findTopRanked(int limit);

    /** 일반 랭킹: 한 판이라도 둔 사용자 중 일반 레이팅 높은 순 상위 limit명. */
    List<User> findTopRankedByClassicRating(int limit);

    /** 피지컬 랭킹: 한 판이라도 둔 사용자 중 피지컬 레이팅 높은 순 상위 limit명. */
    List<User> findTopRankedByPhysicalRating(int limit);
}
