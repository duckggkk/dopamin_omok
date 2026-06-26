package com.dopamin.omok.user.application.port.out;

import java.time.LocalDateTime;

public interface DeleteUserPort {

    /**
     * 사용자를 즉시 삭제한다. 연관 데이터(보유/장착 아이템, 이메일 인증 토큰)는
     * DB의 ON DELETE CASCADE 로 함께 정리된다.
     * 미인증·인증 만료 계정을 회수해 이메일/닉네임 점유를 푸는 용도로 사용한다.
     */
    void deleteById(Long userId);

    /**
     * cutoff 이전에 생성된 게스트(GUEST) 계정을 일괄 삭제하고 삭제 건수를 반환한다.
     * games FK는 ON DELETE SET NULL 이라 대국 행은 남고(상대=null), game_moves·game_players·
     * 보유 아이템 등은 ON DELETE CASCADE 로 함께 정리된다. (쌓이는 익명 계정 청소용)
     */
    int deleteGuestsCreatedBefore(LocalDateTime cutoff);
}
