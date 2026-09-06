package com.stay.common.web;

import com.stay.common.error.ErrorCode;
import java.time.Instant;

/**
 * 자사 API의 성공·실패 공통 응답 본문.
 *
 * <p>상태 코드를 필드로 갖지 않는다. 상태는 HTTP의 것이고 본문의 것이 아니다 (F0 설계 §3).
 */
public record ApiResponse<T>(String code, String message, Instant time, T data) {

    private static final String SUCCESS_CODE = "SUCCESS";
    private static final String SUCCESS_MESSAGE = "Success";

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, Instant.now(), data);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return error(errorCode, errorCode.message());
    }

    /**
     * 패키지 전용이다. 임의 문자열을 `message`로 실을 수 있는 유일한 통로라, 예외의 원본 메시지를 넘기는
     * 수정이 패키지 밖에서는 컴파일되지 않게 막는다 (D-F0-10).
     */
    static ApiResponse<Void> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.code(), message, Instant.now(), null);
    }
}
