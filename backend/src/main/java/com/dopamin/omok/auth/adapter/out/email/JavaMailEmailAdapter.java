package com.dopamin.omok.auth.adapter.out.email;

import com.dopamin.omok.auth.application.port.out.SendEmailPort;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * SMTP(JavaMailSender) 기반 이메일 발송 어댑터 — {@link SendEmailPort} 구현.
 *
 * <p>SMTP 표준으로 동작하므로 Gmail → AWS SES(SMTP) → SendGrid(SMTP) 등은
 * 코드 변경 없이 환경변수(MAIL_HOST/PORT/USERNAME/PASSWORD)만 바꾸면 교체된다.
 * API 방식 제공자로 가려면 이 어댑터만 새로 구현하면 된다(application 계층은 무영향).
 *
 * <p>발송이 실패하면 {@code app.mail.max-attempts} 회까지 재시도한다(기본 3회,
 * 시도 간격 {@code app.mail.retry-delay-ms}ms, 기본 1초). 일시적 SMTP 장애(타임아웃 등)
 * 로 인증 메일이 한 번에 안 나가는 경우를 흡수하기 위함이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JavaMailEmailAdapter implements SendEmailPort {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    /** 최초 1회 + 재시도 포함 총 시도 횟수 상한. */
    @Value("${app.mail.max-attempts:3}")
    private int maxAttempts;

    /** 재시도 사이 대기 시간(ms). */
    @Value("${app.mail.retry-delay-ms:1000}")
    private long retryDelayMs;

    @Override
    public void sendHtml(String to, String subject, String htmlContent) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(fromAddress);
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(htmlContent, true);
                mailSender.send(message);

                if (attempt == 1) {
                    log.info("이메일 발송 완료: {}", to);
                } else {
                    log.info("이메일 발송 완료(재시도 {}회차 성공): {}", attempt, to);
                }
                return;
            } catch (MessagingException | MailException e) {
                log.warn("이메일 발송 실패(시도 {}/{}): to={}, error={}",
                        attempt, maxAttempts, to, e.getMessage());
                if (attempt < maxAttempts) {
                    sleepBeforeRetry();
                }
            }
        }
        // 모든 시도 실패: 예외를 던지지 않고 에러 로그만 남긴다(비동기 발송이라 호출 측에 전파 불가).
        log.error("이메일 발송 최종 실패({}회 시도 모두 실패): to={}", maxAttempts, to);
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(retryDelayMs); // retryDelayMs ms초 쉬고
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt(); // 인터럽트 플래그 복원
        }
    }
}
