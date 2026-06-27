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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock private LoadUserPort loadUserPort;
    @Mock private SaveUserPort saveUserPort;
    @Mock private SavePendingRegistrationPort savePendingRegistrationPort;
    @Mock private LoadPendingRegistrationPort loadPendingRegistrationPort;
    @Mock private DeletePendingRegistrationPort deletePendingRegistrationPort;
    @Mock private EmailService emailService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private EmailVerificationService service;

    private static final String EMAIL = "user@example.com";
    private static final String ENCODED_PW = "{bcrypt}hashed";
    private static final String NICKNAME = "도파민러";
    private static final String CODE = "123456";

    private PendingRegistration pending(String code, LocalDateTime expiresAt, int failedAttempts) {
        return PendingRegistration.restore(EMAIL, ENCODED_PW, NICKNAME, code, expiresAt, failedAttempts);
    }

    // ---------- startRegistration ----------

    @Test
    @DisplayName("가입 시작: RDS에 User를 만들지 않고 Redis 가입 대기만 올린 뒤 인증 메일을 보낸다")
    void startRegistration_savesPendingAndSendsMail_noUserInserted() {
        when(loadUserPort.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(loadUserPort.findByNickname(NICKNAME)).thenReturn(Optional.empty());
        when(loadPendingRegistrationPort.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(loadPendingRegistrationPort.isNicknameReserved(NICKNAME)).thenReturn(false);

        service.startRegistration(EMAIL, ENCODED_PW, NICKNAME);

        verify(savePendingRegistrationPort).save(any(PendingRegistration.class));
        verify(emailService).sendVerificationEmail(eq(EMAIL), anyString());
        // 핵심: 인증 전에는 RDS(users)에 아무것도 쓰지 않는다.
        verify(saveUserPort, never()).save(any());
    }

    @Test
    @DisplayName("가입 시작: 이미 가입을 마친 이메일이면 막는다")
    void startRegistration_emailTakenInDb() {
        when(loadUserPort.findByEmail(EMAIL)).thenReturn(Optional.of(mock(User.class)));

        assertThatThrownBy(() -> service.startRegistration(EMAIL, ENCODED_PW, NICKNAME))
                .isInstanceOfSatisfying(OmokException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
        verify(savePendingRegistrationPort, never()).save(any());
    }

    @Test
    @DisplayName("가입 시작: 이미 사용 중인 닉네임이면 막는다")
    void startRegistration_nicknameTakenInDb() {
        when(loadUserPort.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(loadUserPort.findByNickname(NICKNAME)).thenReturn(Optional.of(mock(User.class)));

        assertThatThrownBy(() -> service.startRegistration(EMAIL, ENCODED_PW, NICKNAME))
                .isInstanceOfSatisfying(OmokException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NICKNAME_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("가입 시작: 인증 진행 중(Redis 대기)인 이메일이면 막는다")
    void startRegistration_emailPendingInRedis() {
        when(loadUserPort.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(loadUserPort.findByNickname(NICKNAME)).thenReturn(Optional.empty());
        when(loadPendingRegistrationPort.findByEmail(EMAIL))
                .thenReturn(Optional.of(pending(CODE, LocalDateTime.now().plusMinutes(3), 0)));

        assertThatThrownBy(() -> service.startRegistration(EMAIL, ENCODED_PW, NICKNAME))
                .isInstanceOfSatisfying(OmokException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("가입 시작: 인증 진행 중인 사람이 선점한 닉네임이면 막는다")
    void startRegistration_nicknameReservedInRedis() {
        when(loadUserPort.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(loadUserPort.findByNickname(NICKNAME)).thenReturn(Optional.empty());
        when(loadPendingRegistrationPort.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(loadPendingRegistrationPort.isNicknameReserved(NICKNAME)).thenReturn(true);

        assertThatThrownBy(() -> service.startRegistration(EMAIL, ENCODED_PW, NICKNAME))
                .isInstanceOfSatisfying(OmokException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NICKNAME_ALREADY_EXISTS));
    }

    // ---------- verifyEmail ----------

    @Test
    @DisplayName("인증 성공: 그 순간 인증 완료된 실제 회원을 만들고, 대기를 지우고, 기본 아이템 이벤트를 발행한다")
    void verifyEmail_success_createsVerifiedUserAndPublishesEvent() {
        when(loadPendingRegistrationPort.findByEmail(EMAIL))
                .thenReturn(Optional.of(pending(CODE, LocalDateTime.now().plusMinutes(3), 0)));
        User saved = mock(User.class);
        when(saved.getId()).thenReturn(1L);
        when(saveUserPort.save(any(User.class))).thenReturn(saved);

        service.verifyEmail(EMAIL, CODE);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(saveUserPort).save(userCaptor.capture());
        User created = userCaptor.getValue();
        assertThat(created.getEmail()).isEqualTo(EMAIL);
        assertThat(created.getNickname()).isEqualTo(NICKNAME);
        assertThat(created.getPassword()).isEqualTo(ENCODED_PW);
        assertThat(created.isEmailVerified()).isTrue();

        verify(deletePendingRegistrationPort).deleteByEmail(EMAIL);
        verify(eventPublisher).publishEvent(any(UserRegisteredEvent.class));
    }

    @Test
    @DisplayName("인증 실패(코드 불일치): 시도 횟수를 올려 다시 저장하고, 회원은 만들지 않는다")
    void verifyEmail_wrongCode_recordsAttempt() {
        when(loadPendingRegistrationPort.findByEmail(EMAIL))
                .thenReturn(Optional.of(pending(CODE, LocalDateTime.now().plusMinutes(3), 0)));

        assertThatThrownBy(() -> service.verifyEmail(EMAIL, "000000"))
                .isInstanceOfSatisfying(OmokException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VERIFICATION_TOKEN_NOT_FOUND));

        verify(savePendingRegistrationPort).save(any(PendingRegistration.class));
        verify(saveUserPort, never()).save(any());
        verify(deletePendingRegistrationPort, never()).deleteByEmail(anyString());
    }

    @Test
    @DisplayName("인증 실패(시도 한도 초과): 대기를 폐기하고 초과 에러를 던진다")
    void verifyEmail_attemptsExceeded_discardsPending() {
        // 직전 실패 4회 → 이번 실패로 5회가 되어 한도 초과
        when(loadPendingRegistrationPort.findByEmail(EMAIL))
                .thenReturn(Optional.of(pending(CODE, LocalDateTime.now().plusMinutes(3), 4)));

        assertThatThrownBy(() -> service.verifyEmail(EMAIL, "000000"))
                .isInstanceOfSatisfying(OmokException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED));

        verify(deletePendingRegistrationPort).deleteByEmail(EMAIL);
        verify(savePendingRegistrationPort, never()).save(any());
    }

    @Test
    @DisplayName("인증 실패(만료): 대기를 지우고 만료 에러를 던진다")
    void verifyEmail_expired() {
        when(loadPendingRegistrationPort.findByEmail(EMAIL))
                .thenReturn(Optional.of(pending(CODE, LocalDateTime.now().minusSeconds(1), 0)));

        assertThatThrownBy(() -> service.verifyEmail(EMAIL, CODE))
                .isInstanceOfSatisfying(OmokException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VERIFICATION_TOKEN_EXPIRED));

        verify(deletePendingRegistrationPort).deleteByEmail(EMAIL);
        verify(saveUserPort, never()).save(any());
    }

    @Test
    @DisplayName("인증: 대기 자체가 없으면(만료 후 소멸) 재가입을 안내한다")
    void verifyEmail_noPending() {
        when(loadPendingRegistrationPort.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyEmail(EMAIL, CODE))
                .isInstanceOfSatisfying(OmokException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REGISTRATION_EXPIRED));
    }

    // ---------- resendVerificationEmail ----------

    @Test
    @DisplayName("재발송: 대기가 살아있으면 새 코드로 다시 저장하고 메일을 보낸다")
    void resend_reissuesAndSends() {
        when(loadPendingRegistrationPort.findByEmail(EMAIL))
                .thenReturn(Optional.of(pending(CODE, LocalDateTime.now().plusMinutes(1), 2)));

        service.resendVerificationEmail(EMAIL);

        verify(savePendingRegistrationPort).save(any(PendingRegistration.class));
        verify(emailService).sendVerificationEmail(eq(EMAIL), anyString());
    }

    @Test
    @DisplayName("재발송: 대기가 사라졌으면(비밀번호도 함께 소멸) 재가입을 안내한다")
    void resend_noPending() {
        when(loadPendingRegistrationPort.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resendVerificationEmail(EMAIL))
                .isInstanceOfSatisfying(OmokException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REGISTRATION_EXPIRED));
    }
}
