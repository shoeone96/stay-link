package com.stay.property.infrastructure;

import com.stay.property.application.SupplierErrorCode;
import com.stay.property.infrastructure.supplier.b.SupplierBResultException;
import io.netty.handler.timeout.ReadTimeoutException;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.codec.DecodingException;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * 공급사 호출이 남긴 예외를 실패 유형으로 바꾸는 단 한 곳. 조합기 뒤에서만 불린다.
 *
 * <p>예외의 cause 사슬을 바깥부터 따라가며 처음 맞는 규칙으로 정한다 — Reactor 와 WebClient 가 원인을
 * 여러 겹으로 감싸므로 맨 바깥 타입만 보면 대부분 분류표에 없는 것으로 보인다.
 */
public final class FailureClassifier {

    private static final Logger log = LoggerFactory.getLogger(FailureClassifier.class);

    private FailureClassifier() {}

    /**
     * 사슬 어디에도 맞는 규칙이 없으면 UNEXPECTED 다. 이때 남기는 ERROR 는 catch-all 이 아니라 "분류표에
     * 없는 예외가 왔다"는 신호다 — 공급사 장애가 아니라 우리 코드나 분류표를 봐야 한다.
     */
    public static SupplierErrorCode classify(Throwable cause) {
        for (Throwable current = cause; current != null; current = current.getCause()) {
            Optional<SupplierErrorCode> matched = matchOne(current);
            if (matched.isPresent()) {
                return matched.get();
            }
        }
        log.error("분류표에 없는 예외가 공급사 호출에서 나왔다 cause={}", cause.getClass().getName(), cause);
        return SupplierErrorCode.UNEXPECTED;
    }

    private static Optional<SupplierErrorCode> matchOne(Throwable current) {
        if (current instanceof BudgetExceededException || current instanceof TimeoutException) {
            return Optional.of(SupplierErrorCode.TIMEOUT);
        }
        if (current instanceof WebClientResponseException http) {
            return Optional.of(byHttpStatus(http.getStatusCode().value()));
        }
        if (current instanceof SupplierBResultException result) {
            return Optional.of(byResultCode(result.resultCode()));
        }
        if (isInvalidResponse(current)) {
            return Optional.of(SupplierErrorCode.INVALID_RESPONSE);
        }
        if (current instanceof WebClientRequestException request) {
            return Optional.of(byTransportFailure(request));
        }
        return Optional.empty();
    }

    /** HTTP 로는 성공했지만 본문을 우리 모델로 만들 수 없는 경우. 전용 예외 두 타입과 디코더 예외만이다. */
    private static boolean isInvalidResponse(Throwable current) {
        return current instanceof InvalidSupplierResponseException
                || current instanceof DecodingException
                || current instanceof UnsupportedMediaTypeException;
    }

    /**
     * 요청이 나가지 못했거나 응답을 받는 중 끊긴 것. 읽기 타임아웃만 TIMEOUT 으로 가르고 나머지(연결 거부·
     * 이름 해석 실패·연결 타임아웃)는 전부 "지금 공급사에 닿을 수 없다"로 본다.
     */
    private static SupplierErrorCode byTransportFailure(WebClientRequestException request) {
        for (Throwable current = request.getCause(); current != null; current = current.getCause()) {
            if (current instanceof ReadTimeoutException) {
                return SupplierErrorCode.TIMEOUT;
            }
        }
        return SupplierErrorCode.UNAVAILABLE;
    }

    /** B 의 코드는 HTTP 상태와 같은 의미를 갖지만 계약에 없는 코드는 "계약과 다른 응답"이지 우리 버그가 아니다. */
    private static SupplierErrorCode byResultCode(String resultCode) {
        return switch (String.valueOf(resultCode)) {
            case "E400" -> SupplierErrorCode.INVALID_REQUEST;
            case "E401" -> SupplierErrorCode.UNAUTHORIZED;
            case "E429" -> SupplierErrorCode.RATE_LIMITED;
            case "E500" -> SupplierErrorCode.SUPPLIER_ERROR;
            case "E503" -> SupplierErrorCode.UNAVAILABLE;
            default -> SupplierErrorCode.INVALID_RESPONSE;
        };
    }

    /**
     * A 는 실패를 HTTP 상태로만 알린다. 계약에 없는 상태는 공급사 장애가 아니라 우리가 모르는 상황이므로
     * 규칙 9 와 같은 수준의 ERROR 를 남긴다 — 이 로그가 없으면 어떤 상태가 왔는지 어디에도 남지 않는다
     * (조합기는 예외 타입만 싣고, 어댑터는 유형만 싣는다). 예외 자체는 싣지 않는다 — 메시지에 요청 URL 이 든다.
     */
    private static SupplierErrorCode byHttpStatus(int status) {
        return switch (status) {
            case 400 -> SupplierErrorCode.INVALID_REQUEST;
            case 401 -> SupplierErrorCode.UNAUTHORIZED;
            case 429 -> SupplierErrorCode.RATE_LIMITED;
            case 500 -> SupplierErrorCode.SUPPLIER_ERROR;
            case 503 -> SupplierErrorCode.UNAVAILABLE;
            default -> {
                log.error("계약에 없는 HTTP 상태가 공급사 호출에서 나왔다 status={}", status);
                yield SupplierErrorCode.UNEXPECTED;
            }
        };
    }
}
