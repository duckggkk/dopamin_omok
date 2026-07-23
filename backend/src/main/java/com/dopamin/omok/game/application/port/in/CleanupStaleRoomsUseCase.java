package com.dopamin.omok.game.application.port.in;

/**
 * 방치된 대기방(WAITING)을 주기적으로 정리한다.
 * <p>
 * 방장이 정상적으로 나가거나 연결이 끊기면 {@code RoomService.handleDisconnect} 가 즉시 방을 닫지만,
 * 그 경로를 타지 못하는 방이 남는다:
 * <ul>
 *   <li>REST 로 방만 만들고 WebSocket 을 한 번도 연결하지 않은 경우(자동화된 방 생성 포함)</li>
 *   <li>서버가 재시작되어, 살아 있던 세션의 disconnect 이벤트가 아무도 처리하지 못한 경우</li>
 * </ul>
 * 이렇게 남은 방을 정리해야 방 목록이 유령 방으로 뒤덮이지 않고,
 * 계정당 활성 방 1개 제약({@code ErrorCode.ROOM_ALREADY_HOSTING})에 갇히는 사용자도 생기지 않는다.
 */
public interface CleanupStaleRoomsUseCase {
    /**
     * {@code staleMinutes} 분보다 오래된 WAITING 방 중 접속자가 아무도 없는 방을 닫고 그 건수를 반환한다.
     * 대국 중(IN_PROGRESS)인 방과 접속자가 남아 있는 방은 절대 건드리지 않는다.
     */
    int cleanupStaleRooms(int staleMinutes);
}
