package com.dopamin.omok.shop.domain.validation;

import com.dopamin.omok.shop.domain.ItemConfig;
import com.dopamin.omok.shop.domain.ItemType;

/**
 * 아이템 타입별 config 검증 전략.
 * 새 코스메틱 타입 추가 시 이 인터페이스 구현 빈만 추가하면 된다 (OCP).
 */
public interface ItemConfigValidator {
    ItemType supportedType();
    void validate(ItemConfig config);

    class InvalidItemConfigException extends RuntimeException {
        public InvalidItemConfigException(String message) {
            super(message);
        }
    }
}
