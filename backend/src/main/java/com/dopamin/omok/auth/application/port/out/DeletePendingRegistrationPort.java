package com.dopamin.omok.auth.application.port.out;

public interface DeletePendingRegistrationPort {

    /** 이메일에 해당하는 가입 대기(인증코드 + 닉네임 예약)를 함께 삭제한다. */
    void deleteByEmail(String email);
}
