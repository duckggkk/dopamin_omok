package com.dopamin.omok.shop.application.port.in;

public interface ChargeCurrencyUseCase {
    int chargeCurrency(Long userId, String packageId);
}
