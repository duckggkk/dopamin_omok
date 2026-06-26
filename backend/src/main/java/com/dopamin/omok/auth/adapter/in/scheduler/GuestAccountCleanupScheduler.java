package com.dopamin.omok.auth.adapter.in.scheduler;

import com.dopamin.omok.auth.application.port.in.CleanupGuestAccountsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 오래된 게스트(비회원) 계정을 주기적으로 정리하는 인바운드 어댑터(스케줄러).
 * "비회원으로 시작"할 때마다 익명 계정이 하나씩 쌓이므로, 일정 기간 지난 것을 매일 한 번 삭제한다.
 * retentionDays 는 application.yml(guest.cleanup.retention-days)로 조정 가능(미설정 시 7일).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuestAccountCleanupScheduler {

    private final CleanupGuestAccountsUseCase cleanupGuestAccountsUseCase;

    @Value("${guest.cleanup.retention-days:7}")
    private int retentionDays;

    /** 매일 04:00(서버 시간)에 실행. */
    @Scheduled(cron = "0 0 4 * * *")
    public void cleanupStaleGuests() {
        int deleted = cleanupGuestAccountsUseCase.cleanupStaleGuests(retentionDays);
        if (deleted > 0) {
            log.info("게스트 계정 정리: 생성 후 {}일 지난 {}건 삭제", retentionDays, deleted);
        }
    }
}
