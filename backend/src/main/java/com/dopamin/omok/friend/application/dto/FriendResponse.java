package com.dopamin.omok.friend.application.dto;

import java.util.UUID;

/** 친구 목록 항목 — 상대 정보 + 나와의 상대 전적. */
public record FriendResponse(
        UUID publicId,
        String nickname,
        String profileImageUrl,
        int classicRating,
        int physicalRating,
        HeadToHead headToHead
) {}
