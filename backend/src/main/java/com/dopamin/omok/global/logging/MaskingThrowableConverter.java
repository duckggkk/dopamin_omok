package com.dopamin.omok.global.logging;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.IThrowableProxy;

/**
 * 예외 스택트레이스 출력까지 {@link PiiMasker} 로 한 번 걸러 내보내는 컨버터.
 *
 * <p><b>왜 필요한가</b> — {@link MaskingMessageConverter}({@code %maskedMsg})는 로그 "메시지"만
 * 마스킹한다. 그런데 logback 은 예외 스택트레이스를 별도 컨버터({@code %wEx} 등)로 찍기 때문에,
 * 예외 메시지에 이메일·토큰이 섞이면(예: {@code "... someone@x.com not found"} 를 메시지로 가진 예외)
 * 그 부분은 마스킹을 우회해 Loki 에 평문으로 남는다. 이 컨버터가 그 구멍을 막는다.
 *
 * <p>logback-spring.xml 에서 {@code %maskedEx} 변환어로 등록해 {@code %wEx} 자리를 대체한다.
 * {@link ThrowableProxyConverter} 를 확장해 렌더링 결과 문자열 전체를 마스킹하므로,
 * 메시지·원인 체인·suppressed 까지 동일한 안전망이 덮는다.
 */
public class MaskingThrowableConverter extends ThrowableProxyConverter {

    @Override
    protected String throwableProxyToString(IThrowableProxy tp) {
        return PiiMasker.mask(super.throwableProxyToString(tp));
    }
}
