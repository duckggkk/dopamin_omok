package com.dopamin.omok.friend.application.dto;

/** 프로필에서 쓰는 '나와 그 사람'의 관계 + 상대 전적. */
public record RelationResponse(
        FriendRelation relation,
        HeadToHead headToHead
) {}
