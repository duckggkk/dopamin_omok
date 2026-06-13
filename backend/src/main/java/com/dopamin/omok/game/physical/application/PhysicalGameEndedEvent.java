package com.dopamin.omok.game.physical.application;

import com.dopamin.omok.game.physical.application.dto.PhysicalReplayData;

/**
 * 피지컬 오목이 '승자가 확정되어' 종료됐을 때(5목 완성/기권) 세션 매니저가 발행하는 이벤트.
 * RoomService 가 수신해 Game 행 finish + 전적 갱신 + room 상태/브로드캐스트 + 리플레이 저장을 처리한다(영속 단일화).
 * 연결 끊김/방장 퇴장처럼 RoomService 가 이미 직접 결과를 기록하는 경로에서는 발행하지 않는다(이중 갱신 방지).
 *
 * @param replay 종료 시점까지 기록된 리플레이(메모리 버퍼 산물). null 일 수 있음.
 */
public record PhysicalGameEndedEvent(String roomCode, Long winnerUserId, PhysicalReplayData replay) {
}
