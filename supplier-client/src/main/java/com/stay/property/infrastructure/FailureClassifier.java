package com.stay.property.infrastructure;

import com.stay.property.application.SupplierErrorCode;
import com.stay.property.infrastructure.supplier.b.SupplierBResultException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
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
 * 공급사 호출이 남긴 예외를 실패 유형으로 바꾸는 단 한 곳.
 *
 * <p>예외의 cause 사슬을 바깥부터 따라가며 처음 맞는 규칙으로 정한다 — Reactor 와 WebClient 가 원인을
 * 여러 겹으로 감싸므로 맨 바깥 타입만 보면 대부분 분류표에 없는 것으로 보인다.
 *
 * <p><b>진입점이 둘인 이유.</b> 같은 판정을 부르는 곳이 셋이 됐다 — 조합기 뒤의 어댑터(보고),
 * 재시도 predicate, 서킷 predicate. 하나로 두면 분류표에 없는 예외 하나에 같은 ERROR 가 세 줄
 * 찍혀 실제 장애 때 가장 시끄러워진다. 그래서 로그를 남기는 {@link #classify} 와 남기지 않는
 * {@link #classifyQuietly} 를 나눈다 (D-F9-11).
 */
public final class FailureClassifier {

    private static final Logger log = LoggerFactory.getLogger(FailureClassifier.class);

    private FailureClassifier() {}

    /**
     * 보고용 진입점. 사슬 어디에도 맞는 규칙이 없거나 계약에 없는 HTTP 상태가 오면 ERROR 를 남긴다 —
     * catch-all 이 아니라 "분류표에 없는 것이 왔다"는 신호이며, 공급사 장애가 아니라 우리 코드나
     * 분류표를 봐야 한다는 뜻이다.
     *
     * <p>계약에 없는 상태 번호는 여기서만 안전하게 남길 수 있다. 어댑터는 유형만 싣고 조합기는 예외
     * 타입만 실으므로(HTTP 오류 예외 메시지에 요청 URL 의 자격 증명이 들어 있다) 이 로그가 없으면
     * 어떤 상태가 왔는지 어디에도 남지 않는다.
     */
    public static SupplierErrorCode classify(Throwable cause) {
        Optional<Match> matched = firstMatch(cause);
        if (matched.isEmpty()) {
            log.error("분류표에 없는 예외가 공급사 호출에서 나왔다 cause={}", cause.getClass().getName(), cause);
            return SupplierErrorCode.UNEXPECTED;
        }
        Match match = matched.get();
        if (match.code() == SupplierErrorCode.UNEXPECTED && match.at() instanceof WebClientResponseException http) {
            log.error("계약에 없는 HTTP 상태가 공급사 호출에서 나왔다 status={}", http.getStatusCode().value());
        }
        return match.code();
    }

    /**
     * 판정용 진입점. 재시도할지·서킷이 셀지를 정하려고 호출마다 불리므로 아무것도 남기지 않는다.
     * 같은 실패의 보고는 {@link #classify} 가 한 번만 한다.
     */
    static SupplierErrorCode classifyQuietly(Throwable cause) {
        return firstMatch(cause).map(Match::code).orElse(SupplierErrorCode.UNEXPECTED);
    }

    /** 규칙에 맞은 유형과 <b>사슬의 어느 예외가 맞았는지</b>. 후자는 보고용 로그가 상세를 꺼내는 데 쓴다. */
    private record Match(Throwable at, SupplierErrorCode code) {}

    private static Optional<Match> firstMatch(Throwable cause) {
        for (Throwable current = cause; current != null; current = current.getCause()) {
            Optional<SupplierErrorCode> matched = matchOne(current);
            if (matched.isPresent()) {
                return Optional.of(new Match(current, matched.get()));
            }
        }
        return Optional.empty();
    }

    private static Optional<SupplierErrorCode> matchOne(Throwable current) {
        if (current instanceof CallNotPermittedException) {
            return Optional.of(SupplierErrorCode.CIRCUIT_OPEN);
        }
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

    /** A 는 실패를 HTTP 상태로만 알린다. 계약에 없는 상태는 공급사 장애가 아니라 우리가 모르는 상황이다. */
    private static SupplierErrorCode byHttpStatus(int status) {
        return switch (status) {
            case 400 -> SupplierErrorCode.INVALID_REQUEST;
            case 401 -> SupplierErrorCode.UNAUTHORIZED;
            case 429 -> SupplierErrorCode.RATE_LIMITED;
            case 500 -> SupplierErrorCode.SUPPLIER_ERROR;
            case 503 -> SupplierErrorCode.UNAVAILABLE;
            default -> SupplierErrorCode.UNEXPECTED;
        };
    }
}
