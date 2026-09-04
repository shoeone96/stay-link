package com.stay.common.web;

import com.stay.common.error.BadRequestException;
import com.stay.common.error.CommonErrorCode;
import com.stay.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 예외를 공통 응답 본문과 HTTP 상태로 변환한다. 우리 예외는 타입이 곧 유형이라 타입마다 상태를 붙이고
 * (D-F0-3), 프레임워크 오류는 자주 나는 것만 개별로 잡는다 (D-F0-11).
 *
 * <p>마지막 그물을 `RuntimeException`으로 받는 이유는 checked 예외를 건드리지 않기 위해서다.
 * `Exception`으로 받으면 `ServletException`을 상속하는 오류(허용되지 않는 메서드 405 등)까지 삼켜
 * Spring이 정한 상태가 500으로 바뀐다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String MESSAGE_FIELDS_SEPARATOR = ": ";
    private static final String FIELD_DELIMITER = ", ";

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException exception,
            HttpServletRequest request) {
        log.warn("Bad request: code={}, method={}, path={}, message={}", exception.errorCode().code(),
                request.getMethod(), request.getRequestURI(), exception.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error(exception.errorCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationFailure(MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        String violatedFields = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField)
                .distinct()
                .sorted()
                .collect(Collectors.joining(FIELD_DELIMITER));
        log.warn("Request validation failed: method={}, path={}, fields={}", request.getMethod(),
                request.getRequestURI(), violatedFields);
        ErrorCode errorCode = CommonErrorCode.INVALID_INPUT;
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(errorCode, errorCode.message() + MESSAGE_FIELDS_SEPARATOR + violatedFields));
    }

    /**
     * 파서가 남긴 원본 메시지는 내부 구조를 드러내므로 로그로만 보낸다 (D-F0-10).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableRequestBody(HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        log.warn("Request body is not readable: method={}, path={}, message={}", request.getMethod(),
                request.getRequestURI(), exception.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error(CommonErrorCode.INVALID_INPUT));
    }

    /**
     * 경로 변수·파라미터를 선언한 타입으로 바꾸지 못한 경우. unchecked라 잡지 않으면 마지막 그물에 걸려
     * 400이 500으로 나간다 (D-F0-13).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        log.warn("Request parameter type mismatch: method={}, path={}, parameter={}", request.getMethod(),
                request.getRequestURI(), exception.getName());
        return ResponseEntity.badRequest().body(ApiResponse.error(CommonErrorCode.INVALID_INPUT));
    }

    /**
     * 상태를 스스로 든 예외. 그 상태가 이미 판단의 결과이므로 다시 매기지 않고 그대로 쓴다 (D-F0-13).
     * 예외가 든 사유 문구는 응답에 싣지 않는다 (D-F0-10).
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleDeclaredStatus(ResponseStatusException exception,
            HttpServletRequest request) {
        HttpStatusCode status = exception.getStatusCode();
        logDeclaredStatus(status, exception, request);
        return ResponseEntity.status(status).body(ApiResponse.error(toErrorCode(status)));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException exception,
            HttpServletRequest request) {
        log.warn("No handler for request: method={}, path={}", request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(CommonErrorCode.NOT_FOUND));
    }

    /**
     * 마지막 그물. 스택은 응답에서 숨기되 로그로 남겨 삼키지 않는다 (CLN-6·D-F0-5).
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(RuntimeException exception,
            HttpServletRequest request) {
        log.error("Unexpected exception: method={}, path={}", request.getMethod(), request.getRequestURI(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(CommonErrorCode.INTERNAL_ERROR));
    }

    /**
     * 5xx는 조치가 필요한 실패라 스택까지 남긴다. 4xx는 클라이언트가 고칠 일이라 한 줄로 족하다 (CLN-9).
     */
    private void logDeclaredStatus(HttpStatusCode status, ResponseStatusException exception,
            HttpServletRequest request) {
        if (status.is5xxServerError()) {
            log.error("Request failed with declared server error: status={}, method={}, path={}", status.value(),
                    request.getMethod(), request.getRequestURI(), exception);
            return;
        }
        log.warn("Request rejected with declared status: status={}, method={}, path={}, message={}", status.value(),
                request.getMethod(), request.getRequestURI(), exception.getMessage());
    }

    private ErrorCode toErrorCode(HttpStatusCode status) {
        if (status.isSameCodeAs(HttpStatus.NOT_FOUND)) {
            return CommonErrorCode.NOT_FOUND;
        }
        if (status.is4xxClientError()) {
            return CommonErrorCode.INVALID_INPUT;
        }
        return CommonErrorCode.INTERNAL_ERROR;
    }
}
