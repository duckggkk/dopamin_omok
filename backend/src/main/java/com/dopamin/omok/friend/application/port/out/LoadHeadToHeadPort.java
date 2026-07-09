package com.dopamin.omok.friend.application.port.out;

import com.dopamin.omok.friend.application.dto.HeadToHead;

import java.util.Collection;
import java.util.Map;

public interface LoadHeadToHeadPort {
    /** 끝난 대국들 기준 meId 의 상대(otherId) 전적(승/패/무). */
    HeadToHead between(Long meId, Long otherId);

    /** 끝난 대국들 기준 meId 와 여러 상대의 전적을 한 번에 집계한다. key 는 상대 userId. */
    Map<Long, HeadToHead> betweenMany(Long meId, Collection<Long> otherIds);
}
