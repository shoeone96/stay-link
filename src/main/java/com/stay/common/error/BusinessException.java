package com.stay.common.error;

/**
 * 비즈니스 예외의 추상 루트. 오류 유형은 이 아래의 예외 클래스가 표현하고, advice가 타입마다 상태 코드를
 * 붙인다 (D-F0-3).
 *
 * <p>예외 메시지는 로그에만 쓰이고 응답 본문에는 `ErrorCode`의 문구가 실린다 (D-F0-10).
 */
public abstract class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    protected BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
