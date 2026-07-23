package com.dopamin.omok.auth.application.service;

import com.dopamin.omok.auth.application.dto.OAuth2LoginResult;
import com.dopamin.omok.auth.application.port.in.OAuth2LoginUseCase;
import com.dopamin.omok.auth.application.port.out.GoogleOAuthPort;
import com.dopamin.omok.auth.domain.GoogleUserInfo;
import com.dopamin.omok.global.common.exception.ErrorCode;
import com.dopamin.omok.global.common.exception.OmokException;
import com.dopamin.omok.global.event.UserRegisteredEvent;
import com.dopamin.omok.user.application.port.out.CheckUserExistsPort;
import com.dopamin.omok.user.application.port.out.DeleteUserPort;
import com.dopamin.omok.user.application.port.out.LoadUserPort;
import com.dopamin.omok.user.application.port.out.SaveUserPort;
import com.dopamin.omok.user.domain.AuthProvider;
import com.dopamin.omok.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 소셜 로그인 처리 서비스.
 *
 * <p>정책:
 * <ul>
 *   <li><b>자동 연동</b> — 구글 이메일과 같은 이메일의 기존 계정이 있으면(일반/소셜 무관) 그 계정으로
 *       로그인한다. 구글이 이메일 소유를 검증하므로 안전하다.</li>
 *   <li><b>신규 생성</b> — 없으면 새 소셜 계정을 만들고, 일반 가입과 동일하게 기본 아이템 지급 이벤트를 발행한다.</li>
 *   <li>미인증 이메일(구글 email_verified=false)은 거부한다.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OAuth2Service implements OAuth2LoginUseCase {

    // nickname 컬럼(30자) + 프로필 수정 검증(2~15자) 모두 만족하도록 자동 닉네임을 15자 이내로 만든다.
    private static final int NICKNAME_BASE_MAX = 11;   // +숫자 4자리 = 최대 15

    private final GoogleOAuthPort googleOAuthPort;
    private final LoadUserPort loadUserPort;
    private final CheckUserExistsPort checkUserExistsPort;
    private final SaveUserPort saveUserPort;
    private final DeleteUserPort deleteUserPort;
    private final TokenIssuer tokenIssuer;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public OAuth2LoginResult loginWithGoogle(String authorizationCode) {
        GoogleUserInfo info = googleOAuthPort.exchangeCodeForUser(authorizationCode);
        if (!info.emailVerified()) {
            throw new OmokException(ErrorCode.OAUTH_EMAIL_NOT_VERIFIED);
        }

        User existing = loadUserPort.findByEmail(info.email()).orElse(null);
        boolean newUser;
        User user;
        if (existing == null) {
            user = createGoogleUser(info);
            newUser = true;
        } else if (!existing.isEmailVerified()) {
            // pre-hijacking 하드닝: 미인증 기존 계정은 누군가 이메일을 선점해 둔 것일 수 있다
            // (미인증이면 로그인된 적이 없어 잃을 데이터도 없다). 삭제하고 구글 신원으로 새로 만들어
            // 선점자가 심어둔 비밀번호가 살아나는 경로를 차단한다. (register 의 reclaim 과 동일 철학)
            deleteUserPort.deleteById(existing.getId());
            user = createGoogleUser(info);
            newUser = true;
        } else {
            // 이미 인증된 정상 소유자 계정 → 그대로 로그인(자동 연동, 비밀번호 보존)
            user = existing;
            newUser = false;
        }

        // 다른 기기 세션 무효화(단일 세션) 후 토큰 발급.
        user.incrementTokenVersion();
        saveUserPort.save(user);

        return new OAuth2LoginResult(tokenIssuer.issue(user), newUser);
    }

    private User createGoogleUser(GoogleUserInfo info) {
        String nickname = generateUniqueNickname(info);
        User user = User.createSocialUser(
                info.email(), nickname, AuthProvider.GOOGLE, info.providerId(), info.pictureUrl());
        saveUserPort.save(user);
        // 일반 가입과 동일하게 기본 아이템(기본 착수음 등) 지급 — shop 모듈이 수신.
        eventPublisher.publishEvent(new UserRegisteredEvent(user.getId()));
        return user;
    }

    /**
     * 고유한 닉네임을 만든다. 구글 표시 이름(없으면 이메일 로컬파트)을 정제한 뒤,
     * 중복되면 4자리 숫자를 붙여 재시도한다. nickname 컬럼(30자) 안에서 안전하게 동작한다.
     */
    private String generateUniqueNickname(GoogleUserInfo info) {
        String base = sanitize(info.name());
        if (base.isBlank() && info.email() != null) {
            base = sanitize(info.email().split("@")[0]);
        }
        if (base.isBlank()) {
            base = "구글유저";
        }
        if (base.length() > NICKNAME_BASE_MAX) {
            base = base.substring(0, NICKNAME_BASE_MAX);
        }

        // 중복 판정은 탈퇴 회원까지 포함해야 UNIQUE 제약과 어긋나지 않는다(CheckUserExistsPort).
        if (!checkUserExistsPort.existsByNickname(base)) {
            return base;
        }
        for (int i = 0; i < 20; i++) {
            String candidate = base + ThreadLocalRandom.current().nextInt(1000, 10000);
            if (!checkUserExistsPort.existsByNickname(candidate)) {
                return candidate;
            }
        }
        // 사실상 도달 불가 — 마지막 안전장치
        return base + UUID.randomUUID().toString().substring(0, 4);
    }

    /** 한글·영문·숫자만 남긴다(공백/특수문자 제거). */
    private String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("[^0-9A-Za-z가-힣]", "");
    }
}
