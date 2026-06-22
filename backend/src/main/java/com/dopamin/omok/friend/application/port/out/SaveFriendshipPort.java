package com.dopamin.omok.friend.application.port.out;

import com.dopamin.omok.friend.domain.Friendship;

public interface SaveFriendshipPort {
    Friendship save(Friendship friendship);
}
