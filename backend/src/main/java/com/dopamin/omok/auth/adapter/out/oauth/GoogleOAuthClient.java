package com.dopamin.omok.auth.adapter.out.oauth;

import com.dopamin.omok.auth.application.port.out.GoogleOAuthPort;
import com.dopamin.omok.auth.config.GoogleOAuthProperties;
import com.dopamin.omok.auth.domain.GoogleUserInfo;
import com.dopamin.omok.global.common.exception.ErrorCode;
import com.dopamin.omok.global.common.exception.OmokException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * 구글 OAuth2 서버와 실제 HTTP 통신을 하는 어댑터({@link GoogleOAuthPort} 구현).
 *
 * <p>2단계로 동작한다:
 * <ol>
 *   <li>토큰 엔드포인트에 인가 코드 + client_secret 을 보내 access token 을 받는다.</li>
 *   <li>userinfo 엔드포인트에 access token 을 실어 이메일·프로필을 받는다.</li>
 * </ol>
 * 어떤 단계든 실패하면 원인(외부 응답 등)을 로그만 남기고 {@link OmokException} 으로 일반화한다.
 */
@Slf4j
@Component
public class GoogleOAuthClient implements GoogleOAuthPort {

    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URI = "https://www.googleapis.com/oauth2/v3/userinfo";

    private final GoogleOAuthProperties properties;
    private final RestClient restClient = RestClient.create();

    public GoogleOAuthClient(GoogleOAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public GoogleUserInfo exchangeCodeForUser(String authorizationCode) {
        try {
            String accessToken = requestAccessToken(authorizationCode);
            GoogleUserInfoResponse info = requestUserInfo(accessToken);
            return new GoogleUserInfo(
                    info.sub(), info.email(), info.emailVerified(), info.name(), info.picture());
        } catch (OmokException e) {
            throw e;
        } catch (Exception e) {
            log.warn("구글 OAuth 토큰 교환/사용자 조회 실패: {}", e.getMessage());
            throw new OmokException(ErrorCode.OAUTH_LOGIN_FAILED);
        }
    }

    private String requestAccessToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", properties.redirectUri());

        GoogleTokenResponse token = restClient.post()
                .uri(TOKEN_URI)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(GoogleTokenResponse.class);

        if (token == null || token.accessToken() == null) {
            throw new OmokException(ErrorCode.OAUTH_LOGIN_FAILED);
        }
        return token.accessToken();
    }

    private GoogleUserInfoResponse requestUserInfo(String accessToken) {
        GoogleUserInfoResponse info = restClient.get()
                .uri(USERINFO_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(GoogleUserInfoResponse.class);

        if (info == null || info.email() == null) {
            throw new OmokException(ErrorCode.OAUTH_LOGIN_FAILED);
        }
        return info;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GoogleTokenResponse(@JsonProperty("access_token") String accessToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GoogleUserInfoResponse(
            String sub,
            String email,
            @JsonProperty("email_verified") boolean emailVerified,
            String name,
            String picture
    ) {
    }
}
