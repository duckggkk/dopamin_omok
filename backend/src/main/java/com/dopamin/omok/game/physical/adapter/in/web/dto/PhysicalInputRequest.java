package com.dopamin.omok.game.physical.adapter.in.web.dto;

import com.dopamin.omok.game.physical.application.PhysicalInputType;
import com.dopamin.omok.game.physical.domain.Direction;
import jakarta.validation.constraints.NotNull;

/**
 * 피지컬 오목 입력 페이로드. 단일 입력 엔드포인트로 받는다.
 * {@code direction} 은 MOVE_START 일 때만 의미가 있다(그 외엔 무시).
 */
public record PhysicalInputRequest(
        @NotNull PhysicalInputType type,
        Direction direction
) {
}
