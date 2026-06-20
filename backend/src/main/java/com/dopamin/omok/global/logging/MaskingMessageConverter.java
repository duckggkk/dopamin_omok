package com.dopamin.omok.global.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * 로그백 패턴에서 메시지를 출력할 때 {@link PiiMasker} 로 한 번 걸러 내보내는 컨버터.
 *
 * <p>logback-spring.xml 에서 {@code %maskedMsg} 라는 변환어로 등록해 사용한다.
 * 기본 {@code %msg} 대신 {@code %maskedMsg} 를 쓰면 모든 appender 의 출력 메시지가
 * 자동으로 마스킹된다(콘솔 → docker logs → Loki 까지 동일하게 적용).
 */
public class MaskingMessageConverter extends MessageConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return PiiMasker.mask(super.convert(event));
    }
}
