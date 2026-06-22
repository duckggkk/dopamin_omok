package com.dopamin.omok.friend.application.port.out;

import com.dopamin.omok.friend.domain.Friendship;

import java.util.List;
import java.util.Optional;

public interface LoadFriendshipPort {
    /** 두 사용자 사이의 관계(방향 무관). 최대 한 건. */
    Optional<Friendship> findBetween(Long userIdA, Long userIdB);

    /** 주어진 사용자의 수락된 친구 관계 목록. */
    List<Friendship> findAcceptedOf(Long userId);

    /** 주어진 사용자가 받은 대기 중 친구 요청 목록. */
    List<Friendship> findIncomingPendingOf(Long userId);
}
