package com.dopamin.omok.game.application.port.in;

/**
 * 서버 재시작으로 런타임 세션이 사라졌지만 DB에는 진행 중으로 남은 피지컬 대국을 정리한다.
 *
 * <p>피지컬 보드와 플레이어 위치는 단일 백엔드 인스턴스의 메모리에만 있으므로 프로세스가
 * 재시작되면 복구할 수 없다. 애플리케이션이 트래픽을 받기 전에 남은 대국을 무효 처리해
 * DB의 방/게임 상태가 실제 런타임과 어긋나지 않게 한다.</p>
 *
 * <p><strong>단일 인스턴스 운영 전용 정책이다.</strong> 여러 인스턴스를 함께 운영할 때는
 * 다른 인스턴스가 소유한 정상 세션을 고아로 오판하지 않도록 게임 소유권 저장소가 먼저 필요하다.</p>
 */
public interface CleanupOrphanedPhysicalGamesUseCase {

    /** 진행 중인 모든 피지컬 게임을 ABANDONED로, 해당 방을 CLOSED로 바꾸고 정리한 게임 수를 반환한다. */
    int cleanupOrphanedPhysicalGames();
}
