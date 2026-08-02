package com.dopamin.omok.game.adapter.in.startup;

import com.dopamin.omok.game.application.port.in.CleanupOrphanedPhysicalGamesUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 애플리케이션이 준비 상태가 되기 전에 복구 불가능한 피지컬 대국을 정리한다.
 * 예외를 삼키지 않는다. 정리 자체가 실패했다면 불일치 상태로 트래픽을 받는 것보다 기동을 실패시켜
 * 운영자가 원인을 확인하도록 하는 편이 안전하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanedPhysicalGameCleanupRunner implements ApplicationRunner {

    private final CleanupOrphanedPhysicalGamesUseCase cleanupUseCase;

    @Override
    public void run(ApplicationArguments args) {
        int abandoned = cleanupUseCase.cleanupOrphanedPhysicalGames();
        if (abandoned > 0) {
            log.warn("서버 재시작 고아 피지컬 대국 {}건을 무효 처리하고 방을 폐쇄했습니다.", abandoned);
        }
    }
}
