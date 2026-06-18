package com.dopamin.omok.auth.application.service;

import com.dopamin.omok.auth.application.port.out.SendEmailPort;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 인증 메일의 "내용"(제목·HTML 템플릿)을 만드는 application 서비스.
 * 실제 발송(SMTP 연결·재시도)은 {@link SendEmailPort} 어댑터에 위임한다(infra 격리).
 * @Async 로 요청 스레드를 막지 않고 비동기 발송한다.
 */
@Service
@RequiredArgsConstructor
public class EmailService {

    private final SendEmailPort sendEmailPort;

    @Async
    public void sendVerificationEmail(String to, String code) {
        String subject = "[도파민 오목] 이메일 인증 코드";
        String content = buildVerificationEmailContent(code);
        sendEmailPort.sendHtml(to, subject, content);
    }

    private String buildVerificationEmailContent(String code) {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; background: #0f0f1e; color: #e0e0e0; padding: 40px; border-radius: 12px;">
                  <h1 style="color: #e0c97f; text-align: center; margin-bottom: 8px;">도파민 오목</h1>
                  <p style="text-align: center; color: #888; margin-bottom: 32px;">이메일 인증 코드</p>
                  <p>안녕하세요! 도파민 오목에 가입해주셔서 감사합니다.</p>
                  <p>아래 6자리 인증 코드를 입력해주세요.</p>
                  <div style="text-align: center; margin: 32px 0;">
                    <div style="display: inline-block; background: #1a1a2e; border: 2px solid #e0c97f; border-radius: 12px; padding: 20px 40px;">
                      <span style="font-size: 2.5rem; font-weight: 700; letter-spacing: 12px; color: #e0c97f;">%s</span>
                    </div>
                  </div>
                  <p style="color: #e05050; text-align: center; font-weight: 600;">⏱ 유효 시간: 3분</p>
                  <hr style="border-color: #333; margin: 24px 0;" />
                  <p style="color: #666; font-size: 0.75rem; text-align: center;">본인이 가입하지 않으셨다면 이 이메일을 무시해주세요.</p>
                </div>
                """.formatted(code);
    }
}
