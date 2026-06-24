package com.dopamin.omok.user.domain;

public enum UserRole {
    USER, ADMIN,
    /** 시스템 봇 계정(피지컬 AI 연습 상대). 로그인 불가, 레이팅·랭킹·전적 미집계. */
    BOT
}
