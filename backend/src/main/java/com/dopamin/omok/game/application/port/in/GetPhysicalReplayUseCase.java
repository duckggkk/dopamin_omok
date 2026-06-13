package com.dopamin.omok.game.application.port.in;

import com.dopamin.omok.game.physical.application.dto.PhysicalReplayData;

public interface GetPhysicalReplayUseCase {
    /** 종료된 게임의 피지컬 리플레이. 기록이 없으면(일반 오목/구버전) null. 해당 게임 참가자만 조회 가능. */
    PhysicalReplayData getReplay(Long gameId, Long userId);
}
