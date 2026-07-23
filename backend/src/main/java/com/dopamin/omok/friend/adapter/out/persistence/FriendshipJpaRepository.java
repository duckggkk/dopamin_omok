package com.dopamin.omok.friend.adapter.out.persistence;

import com.dopamin.omok.friend.domain.Friendship;
import com.dopamin.omok.friend.domain.FriendshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendshipJpaRepository extends JpaRepository<Friendship, Long> {

    @Query("SELECT f FROM Friendship f WHERE (f.requester.id = :a AND f.addressee.id = :b) "
            + "OR (f.requester.id = :b AND f.addressee.id = :a)")
    Optional<Friendship> findBetween(@Param("a") Long a, @Param("b") Long b);

    // 아래 두 조회는 탈퇴(deleted_at)한 상대를 걸러낸다. 친구 관계 행 자체는 지우지 않고
    // 목록에서만 감춘다 — 상대가 탈퇴했다고 남은 사람의 데이터를 물리 삭제할 이유는 없다.
    // (개별 조회 findBetween 은 필터가 필요 없다. 호출부가 findByPublicId/findByNickname 으로
    //  상대를 먼저 찾는데, 그 조회들이 이미 탈퇴자를 돌려주지 않기 때문이다.)

    @Query("SELECT f FROM Friendship f "
            + "JOIN FETCH f.requester "
            + "JOIN FETCH f.addressee "
            + "WHERE f.status = :status "
            + "AND (f.requester.id = :uid OR f.addressee.id = :uid) "
            + "AND f.requester.deletedAt IS NULL AND f.addressee.deletedAt IS NULL "
            + "ORDER BY f.updatedAt DESC")
    List<Friendship> findInvolving(@Param("uid") Long uid, @Param("status") FriendshipStatus status);

    @Query("SELECT f FROM Friendship f "
            + "JOIN FETCH f.requester "
            + "WHERE f.status = :status AND f.addressee.id = :uid "
            + "AND f.requester.deletedAt IS NULL "
            + "ORDER BY f.createdAt DESC")
    List<Friendship> findByAddressee(@Param("uid") Long uid, @Param("status") FriendshipStatus status);
}
