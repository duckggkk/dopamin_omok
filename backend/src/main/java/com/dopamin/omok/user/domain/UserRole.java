package com.dopamin.omok.user.domain;

public enum UserRole {
    USER, ADMIN,
    /** 시스템 봇 계정(피지컬 AI 연습 상대). 로그인 불가, 레이팅·랭킹·전적 미집계. */
    BOT,
    /**
     * 비회원(게스트) 계정. 회원가입 없이 익명으로 발급한다(이메일·비밀번호 없음).
     * 싱글 AI·캐주얼 대국만 허용하며, 레이팅·랭킹·상점·친구·광장 등 멤버 전용 기능은 차단한다.
     */
    GUEST
}
