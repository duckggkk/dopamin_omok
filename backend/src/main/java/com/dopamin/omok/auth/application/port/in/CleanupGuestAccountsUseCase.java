package com.dopamin.omok.auth.application.port.in;

/**
 * 오래된(비활성) 게스트 계정을 정리한다. 회원가입 없이 발급되는 익명 계정이
 * 무한정 쌓이지 않도록 주기적으로 호출된다(스케줄러).
 */
public interface CleanupGuestAccountsUseCase {
    /** retentionDays 보다 오래 전에 생성된 게스트 계정을 삭제하고 삭제 건수를 반환한다. */
    int cleanupStaleGuests(int retentionDays);
}
