package com.dopamin.omok.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 구글 OAuth2 설정값. {@code app.oauth2.google.*} 를 바인딩한다.
 * (OmokApplication 의 @ConfigurationPropertiesScan 으로 자동 등록)
 *
 * <p>redirectUri 는 <b>구글 클라우드 콘솔의 '승인된 리디렉션 URI'와 한 글자도 다르지 않게</b>
 * 일치해야 한다. 인가 요청 URL 과 토큰 교환 요청에 같은 값이 쓰이도록 한 곳에서만 관리한다.
 */
@ConfigurationProperties(prefix = "app.oauth2.google")
public record GoogleOAuthProperties(
        String clientId,
        String clientSecret,
        String redirectUri
) {
}
