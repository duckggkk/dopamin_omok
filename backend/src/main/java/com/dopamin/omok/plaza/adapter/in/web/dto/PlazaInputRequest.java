package com.dopamin.omok.plaza.adapter.in.web.dto;

import com.dopamin.omok.plaza.application.PlazaInputType;
import com.dopamin.omok.plaza.domain.Direction;
import jakarta.validation.constraints.NotNull;

/** 광장 입력 페이로드. direction 은 MOVE_START 일 때만 의미가 있다(그 외 무시). */
public record PlazaInputRequest(
        @NotNull PlazaInputType type,
        Direction direction
) {
}
