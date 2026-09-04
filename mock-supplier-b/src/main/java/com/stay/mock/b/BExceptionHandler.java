package com.stay.mock.b;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * B의 실패를 계약이 정한 모양(HTTP 200 + {@code resultCode})으로 바꾼다. {@code ResponseEntity}를
 * 쓰지 않는 것이 곧 "상태는 언제나 200"이라는 계약이다.
 */
@RestControllerAdvice
public class BExceptionHandler {

    @ExceptionHandler(InvalidRequestException.class)
    public BEnvelope<Void> handleInvalidRequest(InvalidRequestException exception) {
        return BEnvelope.failure(BResultCode.of(exception.kind()));
    }

    @ExceptionHandler(FaultException.class)
    public BEnvelope<Void> handleFault(FaultException exception) {
        return BEnvelope.failure(BResultCode.ofFault(exception.errorCode()));
    }

    /**
     * 숫자 파라미터에 숫자가 아닌 값이 온 경우. 잡지 않으면 프레임워크가 400을 만들어 B의 "실패해도
     * HTTP 200" 계약이 깨진다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public BEnvelope<Void> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return BEnvelope.failure(BResultCode.INVALID_REQUEST);
    }
}
