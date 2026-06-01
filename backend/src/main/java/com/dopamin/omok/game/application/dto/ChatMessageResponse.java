package com.dopamin.omok.game.application.dto;

import com.dopamin.omok.game.domain.StoneColor;

import java.time.LocalDateTime;

public record ChatMessageResponse(
        String senderNickname,
        StoneColor senderColor,
        boolean spectator,
        String content,
        LocalDateTime sentAt
) {}
