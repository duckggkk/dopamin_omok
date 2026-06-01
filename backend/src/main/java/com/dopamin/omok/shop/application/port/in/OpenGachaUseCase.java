package com.dopamin.omok.shop.application.port.in;

import com.dopamin.omok.shop.application.dto.GachaResultResponse;

public interface OpenGachaUseCase {
    GachaResultResponse openBox(Long userId, String boxType);
}
