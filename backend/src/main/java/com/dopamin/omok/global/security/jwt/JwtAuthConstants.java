package com.dopamin.omok.global.security.jwt;

public final class JwtAuthConstants {
    // 전송(HTTP 헤더)
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    // 토큰 내용(claim 키)
    public static final String CLAIM_TOKEN_VERSION = "tokenVersion";
    public static final String CLAIM_PUBLIC_ID = "publicId";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_NICKNAME = "nickName";
    public static final String CLAIM_ROLE = "role";

    private JwtAuthConstants() {
    }
}
