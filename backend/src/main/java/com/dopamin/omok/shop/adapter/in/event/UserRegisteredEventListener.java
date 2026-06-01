package com.dopamin.omok.shop.adapter.in.event;

import com.dopamin.omok.global.event.UserRegisteredEvent;
import com.dopamin.omok.shop.application.port.in.GrantDefaultItemsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 가입 이벤트를 수신해 기본 아이템을 지급한다.
 * 동기 리스너 — 가입 트랜잭션 내에서 함께 처리되어 원자성이 보장된다.
 */
@Component
@RequiredArgsConstructor
public class UserRegisteredEventListener {

    private final GrantDefaultItemsUseCase grantDefaultItemsUseCase;

    @EventListener
    public void handle(UserRegisteredEvent event) {
        grantDefaultItemsUseCase.grantDefaults(event.userId());
    }
}
