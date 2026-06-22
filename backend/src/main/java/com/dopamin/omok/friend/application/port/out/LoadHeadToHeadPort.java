package com.dopamin.omok.friend.application.port.out;

import com.dopamin.omok.friend.application.dto.HeadToHead;

public interface LoadHeadToHeadPort {
    /** 끝난 대국들 기준 meId 의 상대(otherId) 전적(승/패/무). */
    HeadToHead between(Long meId, Long otherId);
}
