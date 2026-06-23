package com.dopamin.omok.auth.domain;

/**
 * 구글에서 받아온 사용자 식별 정보(우리 도메인 표현).
 *
 * @param providerId    구글 사용자 고유 식별자(sub) — 이메일이 바뀌어도 변하지 않는 안정 키
 * @param email         구글 계정 이메일
 * @param emailVerified 구글이 이메일 소유를 검증했는지 여부(미검증이면 로그인 거부)
 * @param name          표시 이름(닉네임 자동 생성의 기초로만 사용)
 * @param pictureUrl    프로필 이미지 URL
 */
public record GoogleUserInfo(
        String providerId,
        String email,
        boolean emailVerified,
        String name,
        String pictureUrl
) {
}
