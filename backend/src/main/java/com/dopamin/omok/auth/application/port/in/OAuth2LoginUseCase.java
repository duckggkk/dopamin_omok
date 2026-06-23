package com.dopamin.omok.auth.application.port.in;

import com.dopamin.omok.auth.application.dto.OAuth2LoginResult;

/**
 * 소셜 로그인 유스케이스. 인가 코드를 받아 우리 서비스의 토큰(JWT)을 발급한다.
 */
public interface OAuth2LoginUseCase {

    /**
     * 구글 인가 코드로 로그인한다. 같은 이메일의 <b>인증된</b> 기존 계정이 있으면 그 계정으로
     * 로그인하고(자동 연동), 없거나 미인증(선점 의심) 계정뿐이면 새 계정을 만든다.
     *
     * @param authorizationCode 구글 콜백으로 받은 1회용 인가 코드
     * @return 토큰 + 신규 계정 여부
     */
    OAuth2LoginResult loginWithGoogle(String authorizationCode);
}
