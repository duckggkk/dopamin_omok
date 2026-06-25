package com.dopamin.omok.game.adapter.in.web.dto;

import com.dopamin.omok.game.domain.ByoyomiOption;
import com.dopamin.omok.game.domain.GameType;
import com.dopamin.omok.game.domain.OmokRule;
import com.dopamin.omok.game.domain.TimeLimit;
import jakarta.validation.constraints.NotNull;

public record CreateRoomRequest(
        @NotNull GameType gameType,
        // 오목 규칙(자유룰/렌주룰). 구버전 클라이언트 호환을 위해 선택값 — 누락 시 자유룰.
        OmokRule omokRule,
        // 랭크전 여부. 구버전 클라이언트 호환을 위해 선택값(Boolean) — 누락 시 랭크전.
        Boolean ranked,
        @NotNull TimeLimit timeLimit,
        @NotNull ByoyomiOption byoyomiOption
) {
    public CreateRoomRequest {
        if (omokRule == null) omokRule = OmokRule.FREESTYLE;
        if (ranked == null) ranked = true;
    }

    public CreateRoomRequest() {
        this(GameType.CLASSIC, OmokRule.FREESTYLE, true, TimeLimit.UNLIMITED, ByoyomiOption.NONE);
    }
}
