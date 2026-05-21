package com.dopamin.omok.game.adapter.in.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record GameMoveRequest(
        @NotNull(message = "행 위치는 필수입니다.")
        @Min(value = 0, message = "행 위치는 0 이상이어야 합니다.")
        @Max(value = 14, message = "행 위치는 14 이하이어야 합니다.")
        Integer row,

        @NotNull(message = "열 위치는 필수입니다.")
        @Min(value = 0, message = "열 위치는 0 이상이어야 합니다.")
        @Max(value = 14, message = "열 위치는 14 이하이어야 합니다.")
        Integer col
) {}
