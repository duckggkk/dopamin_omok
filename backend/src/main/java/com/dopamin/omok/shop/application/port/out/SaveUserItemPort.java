package com.dopamin.omok.shop.application.port.out;

import com.dopamin.omok.shop.domain.UserItem;

public interface SaveUserItemPort {
    void save(UserItem userItem);
}
