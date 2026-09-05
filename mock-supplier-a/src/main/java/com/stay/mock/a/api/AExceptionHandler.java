package com.stay.mock.a.api;

import com.stay.mock.a.fault.FaultException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * A의 실패를 계약이 정한 모양(HTTP 상태 + {@code error}·{@code message})으로 바꾼다.
 *
 * <p>실패 경로는 반드시 로그를 남긴다 (설계 3.5.11). 응답에 담기지 않는 사유 — 어느 파라미터가
 * 틀렸는지 — 를 아는 자리가 여기뿐인 경우가 있다.
 */
@RestControllerAdvice
public class AExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AExceptionHandler.class);

    private static final int RATE_LIMIT_ERROR_CODE = 429;
    private static final int INTERNAL_ERROR_CODE = 500;

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<AErrorResponse> handleInvalidRequest(InvalidRequestException exception) {
        ErrorKind kind = exception.kind();
        log.warn("Request rejected: kind={}", kind);
        return respond(kind.status(), kind.name(), kind.message());
    }

    /**
     * 계약이 정한 고장 코드는 429·500·503 셋뿐이라, 그 밖의 값을 넣으면 일시적 장애로 낸다.
     */
    @ExceptionHandler(FaultException.class)
    public ResponseEntity<AErrorResponse> handleFault(FaultException exception) {
        log.info("Fault applied: errorCode={}", exception.errorCode());
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
     * 아니게 된다. 응답의 {@code message}는 어느 파라미터인지 알려주지 않으므로 로그가 그 자리다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<AErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        ErrorKind kind = ErrorKind.INVALID_PARAMETER;
        log.warn("Request rejected: kind={}, parameter={}", kind, exception.getName());
        return respond(kind.status(), kind.name(), kind.message());
    }

    private ResponseEntity<AErrorResponse> respond(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(new AErrorResponse(error, message));
    }
}
