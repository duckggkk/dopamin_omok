package com.dopamin.omok.auth.application.dto;

/**
 * 소셜 로그인 결과. 토큰과 함께 "이번에 새로 만들어진 계정인지"를 알려준다.
 * 신규 사용자는 프론트에서 닉네임 설정 화면으로 한 번 안내하기 위함이다.
 */
public record OAuth2LoginResult(
        TokenResponse tokens,
        boolean newUser
) {
}
