package com.dopamin.omok.global.event;

/**
 * 신규 유저 가입 시 발행되는 도메인 이벤트.
 * auth 모듈이 발행하고, shop 등 다른 모듈이 수신해 후속 작업(기본 아이템 지급 등)을 수행한다.
 * global(공유 커널)에 두어 모듈 간 직접 의존을 피한다.
 */
public record UserRegisteredEvent(Long userId) {
}
