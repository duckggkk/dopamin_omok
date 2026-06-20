package com.dopamin.omok.user.application.port.out;

public interface DeleteUserPort {

    /**
     * 사용자를 즉시 삭제한다. 연관 데이터(보유/장착 아이템, 이메일 인증 토큰)는
     * DB의 ON DELETE CASCADE 로 함께 정리된다.
     * 미인증·인증 만료 계정을 회수해 이메일/닉네임 점유를 푸는 용도로 사용한다.
     */
    void deleteById(Long userId);
}
