package com.dopamin.omok.shop.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record OpenGachaRequest(
        @NotBlank(message = "상자 타입은 필수입니다.")
        String boxType
) {}
