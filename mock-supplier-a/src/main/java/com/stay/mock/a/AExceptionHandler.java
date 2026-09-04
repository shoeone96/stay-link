package com.stay.mock.a;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * A의 실패를 계약이 정한 모양(HTTP 상태 + {@code error}·{@code message})으로 바꾼다.
 */
@RestControllerAdvice
public class AExceptionHandler {

    private static final int RATE_LIMIT_ERROR_CODE = 429;
    private static final int INTERNAL_ERROR_CODE = 500;

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<AErrorResponse> handleInvalidRequest(InvalidRequestException exception) {
        ErrorKind kind = exception.kind();
        return respond(kind.status(), kind.name(), kind.message());
    }

    /**
     * 계약이 정한 고장 코드는 429·500·503 셋뿐이라, 그 밖의 값을 넣으면 일시적 장애로 낸다.
     */
    @ExceptionHandler(FaultException.class)
    public ResponseEntity<AErrorResponse> handleFault(FaultException exception) {
        return switch (exception.errorCode()) {
            case RATE_LIMIT_ERROR_CODE ->
                    respond(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", "rate limit exceeded");
            case INTERNAL_ERROR_CODE ->
                    respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "internal error");
            default -> respond(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "temporarily unavailable");
        };
    }

    /**
     * 숫자 파라미터에 숫자가 아닌 값이 온 경우. 잡지 않으면 프레임워크가 만든 본문이 나가 계약의 실패 형식이
     * 아니게 된다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<AErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        ErrorKind kind = ErrorKind.INVALID_PARAMETER;
        return respond(kind.status(), kind.name(), kind.message());
    }

    private ResponseEntity<AErrorResponse> respond(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(new AErrorResponse(error, message));
    }
}
