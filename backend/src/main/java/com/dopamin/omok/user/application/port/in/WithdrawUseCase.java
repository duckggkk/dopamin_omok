package com.dopamin.omok.user.application.port.in;

public interface WithdrawUseCase {

    /**
     * 회원 탈퇴. 계정 행은 남기고 개인정보만 파기하는 <b>익명화(soft delete)</b>로 처리한다.
     *
     * @param userId       탈퇴할 회원
     * @param rawPassword  비밀번호를 가진 계정(일반 가입)만 필요. 소셜 전용 계정은 null 허용.
     */
    void withdraw(Long userId, String rawPassword);
}
