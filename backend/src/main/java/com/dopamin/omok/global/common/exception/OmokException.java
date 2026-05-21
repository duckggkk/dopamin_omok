package com.dopamin.omok.global.common.exception;

import lombok.Getter;

@Getter
public class OmokException extends RuntimeException {

    private final ErrorCode errorCode;

    public OmokException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public OmokException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }
}
