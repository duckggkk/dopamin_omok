package com.dopamin.omok.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Objects;

@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        List<String> allowedOrigins
) {

    public CorsProperties {
        allowedOrigins = List.copyOf(Objects.requireNonNull(
                allowedOrigins,
                "app.cors.allowed-origins must be configured"
        ));
    }
}
