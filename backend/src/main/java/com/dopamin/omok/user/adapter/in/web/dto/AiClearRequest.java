package com.dopamin.omok.user.adapter.in.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * AI 사다리 단계 클리어 보고 요청.
 * 단계 범위(1~9)는 여기서 1차 검증하고, "다음 단계만 전진" 무결성은 도메인이 보장한다.
 */
public record AiClearRequest(
        @Min(value = 1, message = "단계는 1 이상이어야 합니다.")
        @Max(value = 9, message = "단계는 9 이하여야 합니다.")
        int level
) {
}
