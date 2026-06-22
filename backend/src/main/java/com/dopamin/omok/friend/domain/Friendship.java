package com.dopamin.omok.friend.domain;

import com.dopamin.omok.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 두 사용자의 친구 관계(요청자→수신자). 한 쌍당 한 행만 존재한다(방향 무관 — 서비스가 양방향 중복을 막는다).
 * PENDING = 요청자가 보낸 친구 요청, ACCEPTED = 수락되어 서로 친구.
 */
@Entity
@Table(name = "friendships",
        uniqueConstraints = @UniqueConstraint(name = "uk_friendship_pair", columnNames = {"requester_id", "addressee_id"}),
        indexes = {
                @Index(name = "idx_friendship_requester", columnList = "requester_id"),
                @Index(name = "idx_friendship_addressee", columnList = "addressee_id")
        })
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Friendship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "addressee_id", nullable = false)
    private User addressee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FriendshipStatus status;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private Friendship(User requester, User addressee) {
        this.requester = requester;
        this.addressee = addressee;
        this.status = FriendshipStatus.PENDING;
    }

    /** 친구 요청 생성(PENDING). */
    public static Friendship request(User requester, User addressee) {
        return new Friendship(requester, addressee);
    }

    public void accept() {
        this.status = FriendshipStatus.ACCEPTED;
    }

    public boolean isPending() {
        return status == FriendshipStatus.PENDING;
    }

    public boolean isAccepted() {
        return status == FriendshipStatus.ACCEPTED;
    }

    /** 이 관계에서 주어진 사용자의 '상대방'을 반환. */
    public User other(Long userId) {
        return requester.getId().equals(userId) ? addressee : requester;
    }

    public boolean isAddressee(Long userId) {
        return addressee.getId().equals(userId);
    }

    public boolean isRequester(Long userId) {
        return requester.getId().equals(userId);
    }
}
