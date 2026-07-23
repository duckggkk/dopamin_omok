package com.dopamin.omok.game.application.port.out;

import com.dopamin.omok.game.domain.GamePlayer;
import com.dopamin.omok.game.domain.PlayerRole;

import java.util.List;
import java.util.Optional;

public interface LoadGamePlayerPort {
    Optional<GamePlayer> findByRoomIdAndUserId(Long roomId, Long userId);
    List<GamePlayer> findByRoomId(Long roomId);
    List<GamePlayer> findByRoomIdAndRole(Long roomId, PlayerRole role);
    int countSpectatorsByRoomId(Long roomId);

    /** 아직 닫히지 않은 방(대기·진행 중)에 참가 중인지 여부. 회원 탈퇴 차단 판정에 쓴다. */
    boolean existsInActiveRoom(Long userId);
}
