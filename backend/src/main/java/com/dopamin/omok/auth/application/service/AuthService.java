package com.dopamin.omok.auth.application.service;

import com.dopamin.omok.auth.application.dto.TokenResponse;
import com.dopamin.omok.auth.application.port.in.LoginUseCase;
import com.dopamin.omok.auth.application.port.in.LogoutUseCase;
import com.dopamin.omok.auth.application.port.in.RefreshTokenUseCase;
import com.dopamin.omok.auth.application.port.in.RegisterUseCase;
import com.dopamin.omok.auth.application.port.in.ResendVerificationEmailUseCase;
import com.dopamin.omok.auth.application.port.in.VerifyEmailUseCase;
import com.dopamin.omok.auth.application.port.out.DeleteRefreshTokenPort;
import com.dopamin.omok.auth.application.port.out.LoadRefreshTokenPort;
import com.dopamin.omok.auth.application.port.out.SaveRefreshTokenPort;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService implements RegisterUseCase, LoginUseCase, RefreshTokenUseCase, LogoutUseCase,
        VerifyEmailUseCase, ResendVerificationEmailUseCase {

    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final DeleteUserPort deleteUserPort;
    private final LoadRefreshTokenPort loadRefreshTokenPort;
    private final SaveRefreshTokenPort saveRefreshTokenPort;
    private final DeleteRefreshTokenPort deleteRefreshTokenPort;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final EmailVerificationService emailVerificationService;
    private final ApplicationEventPublisher eventPublisher;

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
        String accessToken = jwtProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name(), user.getTokenVersion()
        );
        String refreshTokenValue = jwtProvider.generateRefreshToken(user.getId());

        deleteRefreshTokenPort.deleteByUserId(user.getId());

        long expirationMillis = jwtProvider.getRefreshTokenExpirationMillis();
        RefreshToken refreshToken = RefreshToken.of(
                user.getId(),
                refreshTokenValue,
                LocalDateTime.now().plusNanos(expirationMillis * 1_000_000)
        );
        saveRefreshTokenPort.save(refreshToken);

        return TokenResponse.of(accessToken, refreshTokenValue, expirationMillis);
    }
}
