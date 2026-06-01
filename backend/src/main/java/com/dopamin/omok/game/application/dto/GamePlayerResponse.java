package com.dopamin.omok.game.application.dto;

import com.dopamin.omok.game.domain.GamePlayer;
import com.dopamin.omok.game.domain.PlayerRole;
import com.dopamin.omok.game.domain.StoneColor;

import java.util.UUID;

public record GamePlayerResponse(
        UUID userId,
        String nickname,
        String profileImageUrl,
        PlayerRole role,
        StoneColor color,
        Integer remainingSeconds,
        boolean inByoyomi,
        boolean ready
) {
    public static GamePlayerResponse from(GamePlayer gp) {
        return new GamePlayerResponse(
                gp.getUser().getPublicId(),
                gp.getUser().getNickname(),
                gp.getUser().getProfileImageUrl(),
                gp.getRole(),
                gp.getColor(),
                gp.getRemainingSeconds(),
                gp.isInByoyomi(),
                gp.isReady()
        );
    }
}
