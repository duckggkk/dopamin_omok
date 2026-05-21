package com.dopamin.omok.global.security.jwt;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String secret;
    private long accessTokenExpiration;
    private long refreshTokenExpiration;

    // HMAC-SHA256은 최소 32바이트(256bit) 필요 — 기동 시점에 검증
    @PostConstruct
    public void validate() {
        if (secret == null || secret.getBytes().length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 characters (256 bits). " +
                    "Current length: " + (secret == null ? 0 : secret.getBytes().length));
        }
    }
}
