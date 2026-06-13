package com.dopamin.omok.game.physical.application;

import com.dopamin.omok.game.domain.Game;
import com.dopamin.omok.game.domain.GamePlayer;
import com.dopamin.omok.game.domain.Room;
import com.dopamin.omok.game.physical.application.dto.PhysicalReplayData;

import java.util.List;

/**
 * RoomService(로비/방 생명주기)가 피지컬 인게임 런타임을 시작/정리할 때 호출하는 협력 포트.
 * 구현({@code PhysicalGameService})은 메모리 세션만 다루고, 승패 영속은 이벤트로 분리되어 순환 의존이 없다.
 */
public interface PhysicalGameLifecycle {

    /** 피지컬 방 시작: 참가자(흑/백) 색·스킨을 해석해 메모리 세션을 만들고 틱 루프에 등록한다. */
    void start(Room room, Game game, List<GamePlayer> participants);

    /**
     * 강제 종료(연결 끊김/방장 퇴장/방 닫힘): 메모리 세션만 정리한다. DB 결과는 호출 측(RoomService)이 처리한다.
     * @return 그때까지의 리플레이(피지컬 세션이 있었을 때만; 클래식 등은 null). 저장은 호출 측이 한다.
     */
    PhysicalReplayData stopSession(String roomCode);
}
