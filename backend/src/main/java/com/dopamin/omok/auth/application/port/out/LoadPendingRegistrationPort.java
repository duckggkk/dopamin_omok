package com.dopamin.omok.auth.application.port.out;

import com.dopamin.omok.auth.domain.PendingRegistration;

import java.util.Optional;

public interface LoadPendingRegistrationPort {

    /** 이메일로 진행 중인 가입 대기를 조회한다. */
    Optional<PendingRegistration> findByEmail(String email);

    /** 다른 가입 대기가 해당 닉네임을 선점(예약)하고 있으면 true. (인증 진행 중인 닉네임 중복 차단용) */
    boolean isNicknameReserved(String nickname);
}
