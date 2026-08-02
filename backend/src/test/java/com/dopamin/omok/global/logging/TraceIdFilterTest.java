package com.dopamin.omok.global.logging;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영에서는 Caddy 가 X-Request-Id 를 채번해 넘긴다(deploy/Caddyfile). 이 테스트는 Caddy 를
 * 띄우지 않고도 "헤더를 이어받는다"는 계약과, 신뢰할 수 없는 입력에 대한 방어(길이·문자 제한)를
 * 검증한다.
 */
class TraceIdFilterTest {

    private static final String HEADER = "X-Request-Id";

    private final TraceIdFilter filter = new TraceIdFilter();
    private final FilterChain noop = (req, res) -> {
    };

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("들어온 X-Request-Id 를 새로 만들지 않고 그대로 이어받는다")
    void reusesIncomingHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HEADER, "3fa85f64-5717-4562-b3fc-2c963f66afa6");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, noop);

        assertThat(response.getHeader(HEADER)).isEqualTo("3fa85f64-5717-4562-b3fc-2c963f66afa6");
    }

    @Test
    @DisplayName("허용되지 않는 문자는 제거하고 나머지를 재사용한다 (로그 주입 방지)")
    void sanitizesIncomingHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HEADER, "abc!@# 123\n");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, noop);

        assertThat(response.getHeader(HEADER)).isEqualTo("abc123");
    }

    @Test
    @DisplayName("36자를 넘으면 잘라서 쓴다")
    void truncatesOverlongHeader() throws Exception {
        String tooLong = "a".repeat(50);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HEADER, tooLong);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, noop);

        assertThat(response.getHeader(HEADER)).isEqualTo("a".repeat(36));
    }

    @Test
    @DisplayName("헤더가 없으면 8자리 짧은 ID 를 새로 만든다 (Caddy 를 거치지 않은 직접 호출 대비)")
    void generatesShortIdWhenHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, noop);

        assertThat(response.getHeader(HEADER)).hasSize(8);
    }

    @Test
    @DisplayName("허용 문자가 하나도 없으면 빈 문자열 대신 새로 만든다")
    void generatesNewIdWhenHeaderHasNoValidChars() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HEADER, "!@#$%^&*()");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, noop);

        assertThat(response.getHeader(HEADER)).hasSize(8);
    }

    @Test
    @DisplayName("필터 체인이 실행되는 동안 MDC 에 같은 traceId 를 심고, 끝나면 비운다")
    void putsAndClearsMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HEADER, "fixed-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] duringChain = new String[1];
        FilterChain captureMdc = (req, res) -> duringChain[0] = MDC.get(TraceIdFilter.TRACE_ID);

        filter.doFilter(request, response, captureMdc);

        assertThat(duringChain[0]).isEqualTo("fixed-id");
        assertThat(MDC.get(TraceIdFilter.TRACE_ID)).isNull();
    }
}
