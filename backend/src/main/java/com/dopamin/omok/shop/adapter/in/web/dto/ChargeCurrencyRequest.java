package com.dopamin.omok.shop.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ChargeCurrencyRequest(
        @NotBlank(message = "패키지 ID는 필수입니다.")
        String packageId
) {}
