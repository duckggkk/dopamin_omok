package com.dopamin.omok.auth.application.service.support;

import com.dopamin.omok.auth.application.port.out.DeleteEmailVerificationTokenPort;
import com.dopamin.omok.auth.application.port.out.LoadEmailVerificationTokenPort;
import com.dopamin.omok.auth.application.port.out.SaveEmailVerificationTokenPort;
import com.dopamin.omok.auth.application.service.EmailService;
import com.dopamin.omok.auth.domain.EmailVerificationToken;
import com.dopamin.omok.global.common.exception.ErrorCode;
import com.dopamin.omok.global.common.exception.OmokException;
import com.dopamin.omok.user.application.port.out.LoadUserPort;
import com.dopamin.omok.user.application.port.out.SaveUserPort;
import com.dopamin.omok.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailVerificationService {

    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final SaveEmailVerificationTokenPort saveEmailVerificationTokenPort;
    private final LoadEmailVerificationTokenPort loadEmailVerificationTokenPort;
    private final DeleteEmailVerificationTokenPort deleteEmailVerificationTokenPort;
    private final EmailService emailService;

    private static final long VALID_MINUTES = 3;

    public void sendCode(User user) {
        EmailVerificationToken token = EmailVerificationToken.create(user.getId(), VALID_MINUTES);
        saveEmailVerificationTokenPort.save(token);
        emailService.sendVerificationEmail(user.getEmail(), token.getToken());
    }

    /**
     * 인증 유효기간(3분)이 아직 남은 미만료 토큰이 있으면 true.
     * 토큰이 만료됐거나 없으면 false → 해당 미인증 계정의 이메일/닉네임 점유를 풀 수 있다.
     */
    public boolean hasPendingVerification(Long userId) {
        return loadEmailVerificationTokenPort.findLatestByUserId(userId)
                .filter(token -> !token.isExpired())
                .isPresent();
    }

    public void verifyEmail(String email, String code) {
        User user = loadUserPort.findByEmail(email)
                .orElseThrow(() -> new OmokException(ErrorCode.USER_NOT_FOUND));

        if (user.isEmailVerified()) {
            throw new OmokException(ErrorCode.EMAIL_ALREADY_VERIFIED);
        }

        EmailVerificationToken token = loadEmailVerificationTokenPort
                .findByUserIdAndToken(user.getId(), code)
                .orElseGet(() -> recordFailedAttempt(user.getId()));

        //만료된 토큰일시 삭제후 throw
        if (token.isExpired()) {
            deleteEmailVerificationTokenPort.delete(token);
            throw new OmokException(ErrorCode.VERIFICATION_TOKEN_EXPIRED);
        }

        user.verifyEmail();
        saveUserPort.save(user);
        deleteEmailVerificationTokenPort.delete(token);
    }

    public void resendVerificationEmail(String email) {
        User user = loadUserPort.findByEmail(email)
                .orElseThrow(() -> new OmokException(ErrorCode.USER_NOT_FOUND));

        if (user.isEmailVerified()) {
            throw new OmokException(ErrorCode.EMAIL_ALREADY_VERIFIED);
        }

        deleteEmailVerificationTokenPort.deleteByUserId(user.getId());
        sendCode(user);
    }

    private EmailVerificationToken recordFailedAttempt(Long userId) {
        EmailVerificationToken token = loadEmailVerificationTokenPort.findLatestByUserId(userId)
                .orElseThrow(() -> new OmokException(ErrorCode.VERIFICATION_TOKEN_NOT_FOUND));

        if (token.isExpired()) {
            deleteEmailVerificationTokenPort.delete(token);
            throw new OmokException(ErrorCode.VERIFICATION_TOKEN_EXPIRED);
        }

        token.recordFailedAttempt();
        if (token.isAttemptLimitExceeded()) {
            deleteEmailVerificationTokenPort.delete(token);
            throw new OmokException(ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED);
        }
        saveEmailVerificationTokenPort.save(token);
        throw new OmokException(ErrorCode.VERIFICATION_TOKEN_NOT_FOUND);
    }
}
