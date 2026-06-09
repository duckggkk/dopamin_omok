package com.dopamin.omok.game.physical.application;

/**
 * 피지컬 오목이 '승자가 확정되어' 종료됐을 때(5목 완성/기권) 세션 매니저가 발행하는 이벤트.
 * RoomService 가 수신해 Game 행 finish + 전적 갱신 + room 상태/브로드캐스트를 처리한다(영속 단일화).
 * 연결 끊김/방장 퇴장처럼 RoomService 가 이미 직접 결과를 기록하는 경로에서는 발행하지 않는다(이중 갱신 방지).
 */
public record PhysicalGameEndedEvent(String roomCode, Long winnerUserId) {
}
