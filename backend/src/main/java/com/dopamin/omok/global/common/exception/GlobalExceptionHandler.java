package com.dopamin.omok.global.common.exception;

import com.dopamin.omok.global.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OmokException.class)
    public ResponseEntity<ApiResponse<Void>> handleOmokException(OmokException e) {
        HttpStatus status = e.getErrorCode().getHttpStatus();
        // 5xx(서버 잘못)만 ERROR 로 남겨 알림 대상으로 삼고, 4xx(정상 비즈니스 거부:
        // 권한·중복·미존재 등)는 DEBUG 로 낮춘다. 요청 자체는 TraceIdFilter 의 접근 로그(INFO)에
        // 'METHOD URI -> status' 로 이미 남으므로 추적엔 지장이 없다.
        if (status.is5xxServerError()) {
            log.error("OmokException(5xx): {}", e.getMessage(), e);
        } else {
            log.debug("OmokException({}): {}", status.value(), e.getMessage());
        }
        return ResponseEntity
                .status(status)
                .body(ApiResponse.error(e.getMessage()));
    }

    //@Valid 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(
            MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            errors.put(fieldName, message);
        });
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error("입력값 검증에 실패했습니다.", errors));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException e) {
        // 401 은 정상적인 인증 실패(만료 토큰·미로그인 등) — DEBUG. 접근 로그(INFO)에 401 이 남는다.
        log.debug("AuthenticationException: {}", e.getMessage());
        return ResponseEntity
                .status(ErrorCode.UNAUTHORIZED.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.UNAUTHORIZED.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException e) {
        // 403 은 정상적인 권한 거부 — DEBUG.
        log.debug("AccessDeniedException: {}", e.getMessage());
        return ResponseEntity
                .status(ErrorCode.FORBIDDEN.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.FORBIDDEN.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }
}
