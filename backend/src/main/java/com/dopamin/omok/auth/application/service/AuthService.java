package com.dopamin.omok.auth.application.service;

import com.dopamin.omok.auth.application.dto.TokenResponse;
import com.dopamin.omok.auth.application.port.in.CleanupGuestAccountsUseCase;
import com.dopamin.omok.auth.application.port.in.GuestLoginUseCase;
import com.dopamin.omok.auth.application.port.in.LoginUseCase;
import com.dopamin.omok.auth.application.port.in.LogoutUseCase;
import com.dopamin.omok.auth.application.port.in.RefreshTokenUseCase;
import com.dopamin.omok.auth.application.port.in.RegisterUseCase;
import com.dopamin.omok.auth.application.port.in.ResendVerificationEmailUseCase;
import com.dopamin.omok.auth.application.port.in.VerifyEmailUseCase;
import com.dopamin.omok.auth.application.port.out.DeleteRefreshTokenPort;
import com.dopamin.omok.auth.application.port.out.LoadRefreshTokenPort;
import com.dopamin.omok.auth.application.service.support.EmailVerificationService;
import com.dopamin.omok.auth.domain.RefreshToken;
import com.dopamin.omok.global.common.exception.ErrorCode;
import com.dopamin.omok.global.common.exception.OmokException;
import com.dopamin.omok.global.security.jwt.JwtProvider;
import com.dopamin.omok.user.application.port.out.DeleteUserPort;
import com.dopamin.omok.user.application.port.out.LoadUserPort;
import com.dopamin.omok.user.application.port.out.SaveUserPort;
import com.dopamin.omok.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService implements RegisterUseCase, LoginUseCase, RefreshTokenUseCase, LogoutUseCase,
        VerifyEmailUseCase, ResendVerificationEmailUseCase, GuestLoginUseCase, CleanupGuestAccountsUseCase {

    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final DeleteUserPort deleteUserPort;
    private final LoadRefreshTokenPort loadRefreshTokenPort;
    private final DeleteRefreshTokenPort deleteRefreshTokenPort;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final EmailVerificationService emailVerificationService;
    private final TokenIssuer tokenIssuer;

    @Override
    public void register(String email, String password, String nickname) {
        // 미인증 계정은 RDS 에 만들지 않는다. 가입 입력값은 인증을 마칠 때까지 Redis 의 가입 대기로만 머물고,
        // 인증 성공 시점에 비로소 실제 회원이 생성된다(EmailVerificationService.verifyEmail).
        emailVerificationService.startRegistration(email, passwordEncoder.encode(password), nickname);
    }

    @Override
    @Transactional
    public TokenResponse loginAsGuest() {
        // 회원가입 없이 익명 계정을 즉석 생성한다. 이메일은 UUID 로 충돌 없는 내부값을 쓰고,
        // 닉네임은 "게스트####" 형태로 사람이 알아보기 쉽게 부여한다(중복 시 재시도 → UUID 폴백).
        String email = "guest_" + UUID.randomUUID() + "@guest.local";
        String nickname = generateUniqueGuestNickname();

        User guest = User.createGuestUser(email, nickname);
        saveUserPort.save(guest);
        // 게스트는 가벼운 일회성 계정 — 기본 아이템 지급(UserRegisteredEvent)은 생략한다.
        // 미장착 시 클라이언트가 기본 외형/소리로 폴백하므로 대국 표시에 문제 없다.

        return issueTokens(guest);
    }

    @Override
    @Transactional
    public int cleanupStaleGuests(int retentionDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        return deleteUserPort.deleteGuestsCreatedBefore(cutoff);
    }

    /** "게스트####"(1000~9999) 닉네임을 생성하되 중복이면 재시도, 끝내 충돌하면 UUID 조각으로 사실상 유일화한다. */
    private String generateUniqueGuestNickname() {
        for (int i = 0; i < 10; i++) {
            String candidate = "게스트" + (1000 + ThreadLocalRandom.current().nextInt(9000));
            if (loadUserPort.findByNickname(candidate).isEmpty()) {
                return candidate;
            }
        }
        return "게스트" + UUID.randomUUID().toString().substring(0, 6);
    }

    @Override
    @Transactional
    public TokenResponse login(String email, String password) {
        User user = loadUserPort.findByEmail(email)
                .orElseThrow(() -> new OmokException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new OmokException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (!user.isEmailVerified()) {
            throw new OmokException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        user.incrementTokenVersion();
        saveUserPort.save(user);

        return issueTokens(user);
    }

    @Override
    @Transactional
    public void verifyEmail(String email, String code) {
        // 인증 성공 시에만 User 를 INSERT 한다(트랜잭션 경계). 실패 시도 횟수는 Redis 라 롤백과 무관하게 누적된다.
        emailVerificationService.verifyEmail(email, code);
    }

    @Override
    @Transactional
    public void resendVerificationEmail(String email) {
        emailVerificationService.resendVerificationEmail(email);
    }

    @Override
    @Transactional
    public TokenResponse refresh(String token) {
        if (!jwtProvider.validateToken(token)) {
            throw new OmokException(ErrorCode.INVALID_TOKEN);
        }

        RefreshToken refreshToken = loadRefreshTokenPort.findByToken(token)
                .orElseThrow(() -> new OmokException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        if (refreshToken.isExpired()) {
            deleteRefreshTokenPort.delete(refreshToken);
            throw new OmokException(ErrorCode.EXPIRED_TOKEN);
        }

        User user = loadUserPort.findById(refreshToken.getUserId())
                .orElseThrow(() -> new OmokException(ErrorCode.USER_NOT_FOUND));

        deleteRefreshTokenPort.delete(refreshToken);
        return issueTokens(user);
    }

    @Override
    @Transactional
    public void logout(Long userId) {
        deleteRefreshTokenPort.deleteByUserId(userId);
    }

    private TokenResponse issueTokens(User user) {
        return tokenIssuer.issue(user);
    }
}
