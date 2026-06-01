package com.dopamin.omok.shop.application.port.out;

import com.dopamin.omok.shop.domain.UserActiveItem;

public interface SaveUserActiveItemPort {
    void save(UserActiveItem userActiveItem);
}
