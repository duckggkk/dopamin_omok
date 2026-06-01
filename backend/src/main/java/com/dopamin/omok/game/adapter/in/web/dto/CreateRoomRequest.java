package com.dopamin.omok.game.adapter.in.web.dto;

import com.dopamin.omok.game.domain.ByoyomiOption;
import com.dopamin.omok.game.domain.GameType;
import com.dopamin.omok.game.domain.TimeLimit;
import jakarta.validation.constraints.NotNull;

public record CreateRoomRequest(
        @NotNull GameType gameType,
        @NotNull TimeLimit timeLimit,
        @NotNull ByoyomiOption byoyomiOption
) {
    public CreateRoomRequest() {
        this(GameType.CLASSIC, TimeLimit.UNLIMITED, ByoyomiOption.NONE);
    }
}
