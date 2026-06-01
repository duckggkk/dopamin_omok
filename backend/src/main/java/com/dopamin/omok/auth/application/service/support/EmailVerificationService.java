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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
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
        log.info("[이메일 인증] email={} token={}", user.getEmail(), token.getToken());
        emailService.sendVerificationEmail(user.getEmail(), token.getToken());
    }

    public void verifyEmail(String email, String code) {
        User user = loadUserPort.findByEmail(email)
                .orElseThrow(() -> new OmokException(ErrorCode.USER_NOT_FOUND));

        if (user.isEmailVerified()) {
            throw new OmokException(ErrorCode.EMAIL_ALREADY_VERIFIED);
        }

        EmailVerificationToken token = loadEmailVerificationTokenPort
                .findByUserIdAndToken(user.getId(), code)
                .orElseThrow(() -> new OmokException(ErrorCode.VERIFICATION_TOKEN_NOT_FOUND));

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
}
