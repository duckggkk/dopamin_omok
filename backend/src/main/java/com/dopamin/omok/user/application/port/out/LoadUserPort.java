package com.dopamin.omok.user.application.port.out;

import com.dopamin.omok.user.domain.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 사용자 조회. <b>모든 메서드가 탈퇴하지 않은(활성) 회원만 돌려준다</b> — 탈퇴 계정은 존재하지
 * 않는 것처럼 보이므로, 이 포트를 쓰는 서비스는 별도의 탈퇴 검사를 할 필요가 없다.
 * <p>
 * 가입·닉네임 변경의 <b>중복 검사</b>는 탈퇴 회원의 익명 닉네임까지 '사용 중'으로 봐야 하므로
 * 이 포트가 아니라 {@link CheckUserExistsPort} 를 쓸 것.
 */
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
