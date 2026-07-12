package com.dopamin.omok.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 요청마다 짧은 추적 ID(traceId)를 부여해 한 요청에서 나온 모든 로그를 한 묶음으로 잇는다.
 *
 * <p><b>응답 헤더</b> — 같은 ID 를 {@code X-Request-Id} 응답 헤더로 돌려준다.
 *
 * <p>외부에서 {@code X-Request-Id} 를 보내오면 이어받되(분산 추적 호환), 신뢰할 수 없는
 * 입력이므로 길이·문자를 제한해 로그 오염/주입을 막는다.
 *
 * <p>가장 앞 순서로 동작시켜(보안 필터보다 먼저) 인증 실패 로그에도 traceId 가 찍히게 한다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE) //가장 앞순서
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";
    private static final String HEADER = "X-Request-Id";
    private static final int MAX_LEN = 36;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        MDC.put(TRACE_ID, traceId);
        response.setHeader(HEADER, traceId);

        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            logAccess(request, response, start);
            MDC.remove(TRACE_ID);
        }
    }

    /** 들어온 헤더를 이어받거나(정제 후), 없으면 8자리 ID 를 새로 만든다. */
    private String resolveTraceId(HttpServletRequest request) {
        String incoming = request.getHeader(HEADER);
        if (StringUtils.hasText(incoming)) {
            String cleaned = incoming.replaceAll("[^A-Za-z0-9._-]", "");
            if (!cleaned.isEmpty()) {
                return cleaned.length() > MAX_LEN ? cleaned.substring(0, MAX_LEN) : cleaned;
            }
        }
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 한 줄짜리 접근 로그(메서드/경로/상태/소요시간). 디버깅의 1차 단서가 된다.
     * 쿼리스트링은 토큰 등이 섞일 수 있어 일부러 제외하고, 헬스체크(actuator)는 노이즈라 건너뛴다.
     */
    private void logAccess(HttpServletRequest request, HttpServletResponse response, long start) {
        if (!log.isInfoEnabled()) {
            return;
        }
        String uri = request.getRequestURI();
        if (uri != null && uri.contains("/actuator")) {
            return;
        }
        long tookMs = System.currentTimeMillis() - start;
        log.info("{} {} -> {} ({}ms)", request.getMethod(), uri, response.getStatus(), tookMs);
    }
}
