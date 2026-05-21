package com.dopamin.omok.auth.application.service;

import com.dopamin.omok.auth.application.dto.TokenResponse;
import com.dopamin.omok.auth.application.port.in.LoginUseCase;
import com.dopamin.omok.auth.application.port.in.LogoutUseCase;
import com.dopamin.omok.auth.application.port.in.RefreshTokenUseCase;
import com.dopamin.omok.auth.application.port.in.RegisterUseCase;
import com.dopamin.omok.auth.application.port.out.DeleteRefreshTokenPort;
import com.dopamin.omok.auth.application.port.out.LoadRefreshTokenPort;
import com.dopamin.omok.auth.application.port.out.SaveRefreshTokenPort;
import com.dopamin.omok.auth.domain.RefreshToken;
import com.dopamin.omok.user.application.port.out.CheckUserExistsPort;
import com.dopamin.omok.user.application.port.out.LoadUserPort;
import com.dopamin.omok.user.application.port.out.SaveUserPort;
import com.dopamin.omok.user.domain.User;
import com.dopamin.omok.global.common.exception.ErrorCode;
import com.dopamin.omok.global.common.exception.OmokException;
import com.dopamin.omok.global.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService implements RegisterUseCase, LoginUseCase, RefreshTokenUseCase, LogoutUseCase {

    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final CheckUserExistsPort checkUserExistsPort;
    private final LoadRefreshTokenPort loadRefreshTokenPort;
    private final SaveRefreshTokenPort saveRefreshTokenPort;
    private final DeleteRefreshTokenPort deleteRefreshTokenPort;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Override
    @Transactional
    public TokenResponse register(String email, String password, String nickname) {
        if (checkUserExistsPort.existsByEmail(email)) {
            throw new OmokException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (checkUserExistsPort.existsByNickname(nickname)) {
            throw new OmokException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }

        User user = User.createLocalUser(email, passwordEncoder.encode(password), nickname);
        saveUserPort.save(user);

        return issueTokens(user);
    }

    @Override
    @Transactional
    public TokenResponse login(String email, String password) {
        User user = loadUserPort.findByEmail(email)
                .orElseThrow(() -> new OmokException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new OmokException(ErrorCode.INVALID_CREDENTIALS);
        }

        return issueTokens(user);
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
                user.getId(), user.getEmail(), user.getRole().name()
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
