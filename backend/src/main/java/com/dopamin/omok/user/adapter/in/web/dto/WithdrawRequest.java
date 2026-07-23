package com.dopamin.omok.user.adapter.in.web.dto;

/**
 * 회원 탈퇴 요청.
 *
 * @param password 본인 확인용. 일반 가입 계정은 필수, 소셜 전용 계정(비밀번호 없음)은 생략 가능.
 */
public record WithdrawRequest(String password) {}
