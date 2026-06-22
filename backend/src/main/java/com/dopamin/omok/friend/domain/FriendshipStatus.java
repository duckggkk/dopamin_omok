package com.dopamin.omok.friend.domain;

/** 친구 관계 상태. PENDING(요청 대기) → ACCEPTED(친구). 거절/취소/삭제는 행 제거로 처리한다. */
public enum FriendshipStatus {
    PENDING,
    ACCEPTED
}
