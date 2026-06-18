package com.dopamin.omok.auth.application.port.out;

/**
 * 이메일 발송 아웃 포트(infra 격리).
 * "무엇을 보낼지"(제목·본문 템플릿)는 application 계층이 결정하고,
 * "어떻게 보낼지"(SMTP·SES·SendGrid …)는 이 포트를 구현하는 어댑터가 담당한다.
 * 구현체는 발송 실패 시 정해진 횟수만큼 재시도하며, 최종 실패해도 예외를 전파하지 않는다
 * (가입 트랜잭션을 막지 않기 위함 — 호출 측은 @Async 비동기 컨텍스트를 전제로 한다).
 */
public interface SendEmailPort {
    void sendHtml(String to, String subject, String htmlContent);
}
