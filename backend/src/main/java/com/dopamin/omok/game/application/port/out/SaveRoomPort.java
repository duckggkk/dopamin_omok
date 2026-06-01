package com.dopamin.omok.game.application.port.out;

import com.dopamin.omok.game.domain.Room;

public interface SaveRoomPort {
    Room save(Room room);
}
