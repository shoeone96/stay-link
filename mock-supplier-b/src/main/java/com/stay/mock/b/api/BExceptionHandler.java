package com.stay.mock.b.api;

import com.stay.mock.b.fault.FaultException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * B의 실패를 계약이 정한 모양(HTTP 200 + {@code resultCode})으로 바꾼다. {@code ResponseEntity}를
 * 쓰지 않는 것이 곧 "상태는 언제나 200"이라는 계약이다.
 *
 * <p>실패 경로의 로그는 B에서 특히 중요하다 (설계 3.5.11). 계약대로 날짜 형식·코드 개수·음수 인원 세
 * 가지를 {@code E400 INVALID_REQUEST} 하나로 뭉개므로 사유가 응답에서 지워진다 — 로그에도 남기지 않으면
 * 무엇이 틀렸는지 아는 방법이 모의 서버 소스를 읽는 것뿐이다.
 */
@RestControllerAdvice
public class BExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(BExceptionHandler.class);

    @ExceptionHandler(InvalidRequestException.class)
    public BEnvelope<Void> handleInvalidRequest(InvalidRequestException exception) {
        log.warn("Request rejected: kind={}", exception.kind());
        return BEnvelope.failure(BResultCode.of(exception.kind()));
    }

    @ExceptionHandler(FaultException.class)
    public BEnvelope<Void> handleFault(FaultException exception) {
        log.info("Fault applied: errorCode={}", exception.errorCode());
        return BEnvelope.failure(BResultCode.ofFault(exception.errorCode()));
    }

    /**
     * 숫자 파라미터에 숫자가 아닌 값이 온 경우. 잡지 않으면 프레임워크가 400을 만들어 B의 "실패해도
     * HTTP 200" 계약이 깨진다. 응답은 어느 파라미터인지 알려주지 않으므로 로그가 그 자리다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public BEnvelope<Void> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        log.warn("Request rejected: kind={}, parameter={}", ErrorKind.INVALID_PARAMETER, exception.getName());
        return BEnvelope.failure(BResultCode.INVALID_REQUEST);
    }
}
