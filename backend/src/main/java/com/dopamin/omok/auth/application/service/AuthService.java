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
import com.dopamin.omok.global.event.UserRegisteredEvent;
import com.dopamin.omok.global.security.jwt.JwtProvider;
import com.dopamin.omok.user.application.port.out.DeleteUserPort;
import com.dopamin.omok.user.application.port.out.LoadUserPort;
import com.dopamin.omok.user.application.port.out.SaveUserPort;
import com.dopamin.omok.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;
    private final TokenIssuer tokenIssuer;

    @Override
    @Transactional
    public void register(String email, String password, String nickname) {
        User emailOwner = loadUserPort.findByEmail(email).orElse(null);
        User nicknameOwner = loadUserPort.findByNickname(nickname).orElse(null);

        // 인증을 마쳤거나(정상 계정) 인증 유효기간(3분)이 아직 남은 미인증 계정이
        // 점유 중이면 가입을 막는다.
        if (isStillOccupied(emailOwner)) {
            throw new OmokException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (isStillOccupied(nicknameOwner)) {
            throw new OmokException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }

        // 여기까지 왔다면 충돌 계정은 '미인증 + 인증 만료' 상태뿐이다.
        // 이메일/닉네임 점유를 풀기 위해 해당 유령 계정을 회수(삭제)한다.
        // (이메일·닉네임이 같은 계정을 가리키면 한 번만 삭제)
        reclaimExpiredUnverified(emailOwner);
        if (nicknameOwner != null
                && (emailOwner == null || !nicknameOwner.getId().equals(emailOwner.getId()))) {
            reclaimExpiredUnverified(nicknameOwner);
        }

        User user = User.createLocalUser(email, passwordEncoder.encode(password), nickname);
        saveUserPort.save(user);

        // 가입 직후 기본 아이템(기본 착수음 등) 지급 — shop 모듈이 수신해 처리
        eventPublisher.publishEvent(new UserRegisteredEvent(user.getId()));

        emailVerificationService.sendCode(user);
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

    /** 인증 완료 계정이거나 인증 유효기간이 아직 남은 미인증 계정이면 이메일/닉네임이 점유 중이다. */
    private boolean isStillOccupied(User existing) {
        if (existing == null) {
            return false;
        }
        if (existing.isEmailVerified()) {
            return true;
        }
        return emailVerificationService.hasPendingVerification(existing.getId());
    }

    /** 미인증·인증 만료 계정을 삭제한다. 연관 데이터는 DB의 ON DELETE CASCADE 로 함께 정리된다. */
    private void reclaimExpiredUnverified(User existing) {
        if (existing != null) {
            deleteUserPort.deleteById(existing.getId());
        }
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
    @Transactional(noRollbackFor = OmokException.class)
    public void verifyEmail(String email, String code) {
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
