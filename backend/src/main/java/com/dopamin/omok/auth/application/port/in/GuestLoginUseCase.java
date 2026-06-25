package com.dopamin.omok.auth.application.port.in;

import com.dopamin.omok.auth.application.dto.TokenResponse;

/**
 * 비회원(게스트) 로그인. 회원가입 없이 익명 계정을 즉석에서 발급하고 토큰을 내려준다.
 * 발급된 계정은 GUEST 역할이라 멤버 전용 기능(랭크전·상점·친구·광장 등)이 차단된다.
 */
public interface GuestLoginUseCase {
    TokenResponse loginAsGuest();
}
