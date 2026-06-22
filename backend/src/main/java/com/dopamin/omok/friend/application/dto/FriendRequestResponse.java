package com.dopamin.omok.friend.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/** 내가 받은 친구 요청 항목 — 요청 보낸 사람 정보. */
public record FriendRequestResponse(
        UUID publicId,
        String nickname,
        String profileImageUrl,
        LocalDateTime requestedAt
) {}
