package com.dopamin.omok.game.adapter.in.scheduler;

import com.dopamin.omok.game.application.port.in.CleanupStaleRoomsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 방치된 대기방을 주기적으로 닫는 인바운드 어댑터(스케줄러).
 * 게스트 계정 정리({@code GuestAccountCleanupScheduler})와 같은 패턴이다.
 * <p>
 * 주기가 30분인 이유: 유령 방은 방 목록을 지저분하게 만들 뿐 서버를 위협하지는 않으므로
 * 자주 돌 필요가 없고, 반대로 하루 한 번이면 제약({@code ROOM_ALREADY_HOSTING})에 걸린
 * 사용자가 너무 오래 기다리게 된다. 다만 대부분의 경우 새 방을 만들 때 자동 회수가
 * 먼저 동작하므로, 이 스케줄러는 방 목록 청소가 주 역할이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StaleRoomCleanupScheduler {

    private final CleanupStaleRoomsUseCase cleanupStaleRoomsUseCase;

    /** 이 시간(분)보다 오래된 WAITING 방이 정리 대상. application.yml 로 조정 가능. */
    @Value("${room.cleanup.stale-minutes:120}")
    private int staleMinutes;

    /** 매시 정각과 30분에 실행. */
    @Scheduled(cron = "0 0,30 * * * *")
    public void cleanupStaleRooms() {
        int closed = cleanupStaleRoomsUseCase.cleanupStaleRooms(staleMinutes);
        if (closed > 0) {
            log.info("방치된 대기방 정리: {}분 이상 방치되고 접속자가 없는 {}건 폐쇄", staleMinutes, closed);
        }
    }
}
