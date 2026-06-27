package com.dopamin.omok.auth.application.service.support;

import com.dopamin.omok.auth.application.port.out.DeletePendingRegistrationPort;
import com.dopamin.omok.auth.application.port.out.LoadPendingRegistrationPort;
import com.dopamin.omok.auth.application.port.out.SavePendingRegistrationPort;
import com.dopamin.omok.auth.application.service.EmailService;
import com.dopamin.omok.auth.domain.PendingRegistration;
import com.dopamin.omok.global.common.exception.ErrorCode;
import com.dopamin.omok.global.common.exception.OmokException;
import com.dopamin.omok.global.event.UserRegisteredEvent;
import com.dopamin.omok.user.application.port.out.LoadUserPort;
import com.dopamin.omok.user.application.port.out.SaveUserPort;
import com.dopamin.omok.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 이메일 인증 기반 회원가입의 핵심 로직.
 * <p>
 * 미인증 계정을 RDS 에 만들지 않는다. 가입 입력값은 인증을 마칠 때까지 {@link PendingRegistration}
 * 으로 Redis 에만 머물고(TTL 자동 만료), <b>인증에 성공하는 순간에만</b> 실제 {@link User} 를
 * RDS 에 INSERT 한다. → 인증을 끝내지 않은 유령 계정이 애초에 생기지 않으므로 회수/청소 로직이 불필요하다.
 */
@Component
@RequiredArgsConstructor
public class EmailVerificationService {

    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final SavePendingRegistrationPort savePendingRegistrationPort;
    private final LoadPendingRegistrationPort loadPendingRegistrationPort;
    private final DeletePendingRegistrationPort deletePendingRegistrationPort;
    private final EmailService emailService;
    private final ApplicationEventPublisher eventPublisher;

    private static final long VALID_MINUTES = 3;

    /**
     * 회원가입 시작. RDS 에는 아무것도 쓰지 않고, 가입 대기만 Redis 에 올린 뒤 인증 메일을 보낸다.
     * 이미 가입을 마친(=users 에 존재) 이메일/닉네임이거나, 인증 진행 중(Redis 대기)인 이메일/닉네임이면 막는다.
     */
    public void startRegistration(String email, String encodedPassword, String nickname) {
        if (loadUserPort.findByEmail(email).isPresent()) {
            throw new OmokException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (loadUserPort.findByNickname(nickname).isPresent()) {
            throw new OmokException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }
        // 인증 진행 중인(아직 회원이 되지 않은) 사람이 같은 이메일/닉네임을 선점하고 있으면 막는다.
        if (loadPendingRegistrationPort.findByEmail(email).isPresent()) {
            throw new OmokException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (loadPendingRegistrationPort.isNicknameReserved(nickname)) {
            throw new OmokException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }

        PendingRegistration pending = PendingRegistration.create(email, encodedPassword, nickname, VALID_MINUTES);
        savePendingRegistrationPort.save(pending);
        emailService.sendVerificationEmail(email, pending.getCode());
    }

    /**
     * 인증코드 검증. 성공하면 그 순간 실제 회원을 RDS 에 만들고 기본 아이템 지급 이벤트를 발행한다.
     * 코드가 틀리면 시도 횟수를 올리고, 한도를 넘으면 대기 자체를 폐기한다.
     */
    public void verifyEmail(String email, String code) {
        PendingRegistration pending = loadPendingRegistrationPort.findByEmail(email)
                .orElseThrow(() -> new OmokException(ErrorCode.REGISTRATION_EXPIRED));

        if (pending.isExpired()) {
            deletePendingRegistrationPort.deleteByEmail(email);
            throw new OmokException(ErrorCode.VERIFICATION_TOKEN_EXPIRED);
        }

        if (!pending.matches(code)) {
            pending.recordFailedAttempt();
            if (pending.isAttemptLimitExceeded()) {
                deletePendingRegistrationPort.deleteByEmail(email);
                throw new OmokException(ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED);
            }
            savePendingRegistrationPort.save(pending);
            throw new OmokException(ErrorCode.VERIFICATION_TOKEN_NOT_FOUND);
        }

        // 인증 성공 — 바로 이 순간에만 진짜 회원을 RDS 에 만든다.
        User user = User.createLocalUser(pending.getEmail(), pending.getEncodedPassword(), pending.getNickname());
        user.verifyEmail();
        User saved;
        try {
            saved = saveUserPort.save(user);
        } catch (DataIntegrityViolationException e) {
            // 인증 대기 중 다른 가입이 같은 이메일/닉네임을 먼저 확정한 극히 드문 레이스.
            // DB UNIQUE 제약이 최종 방어선이다. 대기를 폐기하고 충돌로 응답한다.
            deletePendingRegistrationPort.deleteByEmail(email);
            throw new OmokException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        deletePendingRegistrationPort.deleteByEmail(email);
        // 가입 직후 기본 아이템(기본 착수음 등) 지급 — shop 모듈이 수신해 처리
        eventPublisher.publishEvent(new UserRegisteredEvent(saved.getId()));
    }

    /**
     * 인증 메일 재발송. 가입 대기가 아직 살아있으면 같은 입력값으로 코드만 새로 발급해 다시 보낸다.
     * 대기가 만료돼 사라졌으면(비밀번호 등 입력값이 Redis 에서 함께 소멸) 재발송할 수 없으므로 재가입을 안내한다.
     */
    public void resendVerificationEmail(String email) {
        PendingRegistration pending = loadPendingRegistrationPort.findByEmail(email)
                .orElseThrow(() -> new OmokException(ErrorCode.REGISTRATION_EXPIRED));

        PendingRegistration reissued = pending.reissue(VALID_MINUTES);
        savePendingRegistrationPort.save(reissued);
        emailService.sendVerificationEmail(email, reissued.getCode());
    }
}
