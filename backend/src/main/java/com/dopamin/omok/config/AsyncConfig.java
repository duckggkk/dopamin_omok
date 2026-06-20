package com.dopamin.omok.config;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 비동기 작업(@Async, 예: 인증 이메일 발송)용 설정.
 * 기본 SimpleAsyncTaskExecutor(요청당 새 스레드)를 풀 기반 실행기로 교체하고,
 * void 반환 @Async 메서드에서 던져진 예외를 로깅한다.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("omok-async-");
        // 요청 스레드의 로그 문맥(traceId 등)을 비동기 스레드로 복사 →
        // 비동기 이메일 발송 로그도 원래 요청과 같은 traceId 로 묶여 추적된다.
        executor.setTaskDecorator(AsyncConfig::wrapWithMdc);
        executor.initialize();
        return executor;
    }

    /** 제출 시점의 MDC(로그 문맥)를 실행 스레드에 입혔다가 끝나면 비워, 스레드 재사용 시 오염을 막는다. */
    private static Runnable wrapWithMdc(Runnable task) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            if (context != null) {
                MDC.setContextMap(context);
            }
            try {
                task.run();
            } finally {
                MDC.clear();
            }
        };
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            log.error("Async 작업 예외: method={}, params={}", method.getName(), Arrays.toString(params), ex);
            // 기본 핸들러도 호출(표준 로깅 유지)
            new SimpleAsyncUncaughtExceptionHandler().handleUncaughtException(ex, method, params);
        };
    }
}
