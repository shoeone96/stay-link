package com.stay.common.error;

/**
 * 비즈니스 예외의 루트. 예외 메시지는 로그에만 쓰이고 응답 본문에는 `ErrorCode`의 고정 문구가 실린다 (D-F0-10).
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    protected BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
