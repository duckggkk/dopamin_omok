package com.dopamin.omok.auth.application.port.out;

import com.dopamin.omok.auth.domain.GoogleUserInfo;

/**
 * 구글 OAuth2 서버와 통신하는 출력 포트. 인가 코드를 사용자 정보로 교환한다.
 * (실제 HTTP 호출은 adapter/out/oauth 의 어댑터가 담당 — application 계층은 무지)
 */
public interface GoogleOAuthPort {

    /** 인가 코드 → (토큰 교환) → 구글 사용자 정보. 실패 시 OmokException(OAUTH_LOGIN_FAILED). */
    GoogleUserInfo exchangeCodeForUser(String authorizationCode);
}
