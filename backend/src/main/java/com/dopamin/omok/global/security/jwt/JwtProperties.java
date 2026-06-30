package com.dopamin.omok.global.security.jwt;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private final String secret;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtProperties(String secret, long accessTokenExpiration, long refreshTokenExpiration) {
        // 검증도 여기서 같이 가능
        this.secret = secret;
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }
}