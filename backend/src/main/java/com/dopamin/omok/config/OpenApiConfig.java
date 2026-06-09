package com.dopamin.omok.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI(Swagger) 문서 설정.
 * 컨텍스트 경로(/api) 기준으로 /api/swagger-ui.html, /api/v3/api-docs 에 노출된다.
 * JWT Bearer 인증을 전역 보안 스킴으로 등록해 모바일/외부 클라이언트가
 * 동일한 인증 방식으로 API를 호출할 수 있도록 한다.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI omokOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("도파민 오목 API")
                        .description("오목 웹/모바일 클라이언트 공용 REST API 문서")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
