package com.stay.common.error;

/**
 * 잘못된 요청. advice가 이 타입을 400으로 옮기며, 하위 예외도 모두 같은 자리로 온다.
 *
 * <p>메시지를 필수 인자로 둔 이유는 응답이 아니라 로그를 위해서다. 응답에는 `ErrorCode`의 고정 문구가
 * 나가므로(D-F0-10), 무엇이 잘못됐는지는 식별자가 담긴 이 메시지로만 남는다 (CLN-6·CLN-9).
 */
public class BadRequestException extends BusinessException {

    public BadRequestException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
