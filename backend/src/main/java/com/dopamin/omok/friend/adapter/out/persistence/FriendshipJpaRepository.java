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

    @Query("SELECT f FROM Friendship f "
            + "JOIN FETCH f.requester "
            + "JOIN FETCH f.addressee "
            + "WHERE f.status = :status "
            + "AND (f.requester.id = :uid OR f.addressee.id = :uid) ORDER BY f.updatedAt DESC")
    List<Friendship> findInvolving(@Param("uid") Long uid, @Param("status") FriendshipStatus status);

    @Query("SELECT f FROM Friendship f "
            + "JOIN FETCH f.requester "
            + "WHERE f.status = :status AND f.addressee.id = :uid "
            + "ORDER BY f.createdAt DESC")
    List<Friendship> findByAddressee(@Param("uid") Long uid, @Param("status") FriendshipStatus status);
}
