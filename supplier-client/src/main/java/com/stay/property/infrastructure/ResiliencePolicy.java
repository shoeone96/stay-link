package com.stay.property.infrastructure;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * 재시도·서킷 수치를 한 값으로 묶고 <b>시도별 상한을 유도</b>한다. 유도가 이 타입의 존재 이유다 —
 * 시도별 상한을 별도 프로퍼티로 두면 "재시도를 포함한 총 소요가 호출당 상한을 넘지 않는다"가 설정을
 * 맞게 적었을 때만 성립하는 우연이 된다(설계 §3.2).
 *
 * <p>서킷 수치({@code slidingWindowSize}·{@code minimumNumberOfCalls}·{@code failureRateThreshold})의
 * 단위는 <b>논리 호출이 아니라 시도</b>다. 재시도가 서킷 바깥에 있어 각 시도가 따로 창에 들어가기
 * 때문이며, 그래도 서킷이 보는 실패율은 시도별 실패율과 정확히 같다(설계 §3.2.1).
 */
public record ResiliencePolicy(
        int maxAttempts,
        Duration minBackoff,
        Duration maxBackoff,
        double jitterFactor,
        float failureRateThreshold,
        int slidingWindowSize,
        int minimumNumberOfCalls,
        Duration waitDurationInOpenState,
        int permittedCallsInHalfOpen) {

    /**
     * 백오프가 시도마다 커지는 배수. 설정으로 노출하지 않는다 — 시도가 최대 세 번이라
     * {@code minBackoff}·{@code maxBackoff} 두 값이 이미 구간을 정하고, 배수는 그 사이를 어떻게
     * 오르는지만 바꾼다. 손잡이를 늘릴 만큼의 차이가 아니라 라이브러리 기본값을 그대로 쓴다.
     */
    private static final double BACKOFF_MULTIPLIER = IntervalFunction.DEFAULT_MULTIPLIER;

    /**
     * 값 검사가 여기 있는 이유는 이 타입이 설정 두 벌(검색용·수집용)의 공통 도착지이기 때문이다 —
     * 바인딩 지점마다 같은 검사를 복사하면 한쪽만 고쳐져 어긋난다. 메시지가 자바 필드명이 아니라
     * <b>설정 키의 뒷부분</b>인 것도 그래서다. 앞에 붙는 prefix 는 바인딩 지점이 안다.
     */
    public ResiliencePolicy {
        requireAtLeast("max-attempts", maxAttempts, 1);
        requirePositive("min-backoff", minBackoff);
        requirePositive("max-backoff", maxBackoff);
        requireOrdered("max-backoff", maxBackoff, "min-backoff", minBackoff);
        requireJitterFactor(jitterFactor);
        requireAtLeast("sliding-window-size", slidingWindowSize, 1);
        requireAtLeast("minimum-number-of-calls", minimumNumberOfCalls, 1);
        requireWithinWindow(minimumNumberOfCalls, slidingWindowSize);
        requireFailureRateThreshold(failureRateThreshold);
        requirePositive("wait-duration-in-open-state", waitDurationInOpenState);
        requireAtLeast("permitted-calls-in-half-open", permittedCallsInHalfOpen, 1);
    }

    private static void requireAtLeast(String key, int value, int minimum) {
        if (value < minimum) {
            throw new IllegalArgumentException("%s 는 %d 이상이어야 한다: %d".formatted(key, minimum, value));
        }
    }

    private static void requirePositive(String key, Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("%s 는 0보다 커야 한다: %s".formatted(key, value));
        }
    }

    private static void requireOrdered(String largerKey, Duration larger, String smallerKey, Duration smaller) {
        if (larger.compareTo(smaller) < 0) {
            throw new IllegalArgumentException(
                    "%s 는 %s 보다 작을 수 없다: %s < %s".formatted(largerKey, smallerKey, larger, smaller));
        }
    }

    /** 위 끝이 열린 구간인 이유는 지터 1.0 이 "대기가 0 이 될 수 있다"는 뜻이라 백오프가 사라지기 때문이다. */
    private static void requireJitterFactor(double jitterFactor) {
        if (jitterFactor < 0 || jitterFactor >= 1) {
            throw new IllegalArgumentException("jitter-factor 는 0 이상 1 미만이어야 한다: %s".formatted(jitterFactor));
        }
    }

    /** 0 이면 성공만 해도 열리고, 100 을 넘으면 어떤 실패율로도 열리지 않아 서킷이 죽은 코드가 된다. */
    private static void requireFailureRateThreshold(float failureRateThreshold) {
        if (failureRateThreshold <= 0 || failureRateThreshold > 100) {
            throw new IllegalArgumentException(
                    "failure-rate-threshold 는 0 초과 100 이하여야 한다: %s".formatted(failureRateThreshold));
        }
    }

    /**
     * 최소 호출 수가 창보다 크면 창이 다 차도 판정에 필요한 표본이 모이지 않아 <b>서킷이 영영 열리지
     * 않는다.</b> 값 하나하나는 멀쩡하고 조합만 틀린 경우라 따로 막는다.
     */
    private static void requireWithinWindow(int minimumNumberOfCalls, int slidingWindowSize) {
        if (minimumNumberOfCalls > slidingWindowSize) {
            throw new IllegalArgumentException(
                    "minimum-number-of-calls 는 sliding-window-size 보다 클 수 없다: %d > %d"
                            .formatted(minimumNumberOfCalls, slidingWindowSize));
        }
    }

    /**
     * 시도 하나에 허용하는 시간. {@code perCall} 은 조합기가 재시도 <b>전체</b>에 거는 상한이므로,
     * 최악의 백오프까지 다 쓰고도 시도 수만큼 나눠 가질 수 있는 값을 돌려준다.
     *
     * <p>밀리초로 자르는 이유는 남는 나노초가 상한을 조이는 쪽으로만 움직여 안전하고, 로그·설정에서
     * 읽히는 단위가 밀리초이기 때문이다.
     */
    public Duration attemptTimeout(Duration perCall) {
        Duration remaining = perCall.minus(worstCaseBackoffTotal());
        if (remaining.isZero() || remaining.isNegative()) {
            throw new IllegalArgumentException(
                    "per-call 이 재시도 백오프 합보다 커야 시도별 상한을 유도할 수 있다: per-call=%s, 백오프 합=%s, 시도=%d"
                            .formatted(perCall, worstCaseBackoffTotal(), maxAttempts));
        }
        return remaining.dividedBy(maxAttempts).truncatedTo(ChronoUnit.MILLIS);
    }

    /**
     * 재시도 설정. 대상 판정을 예외 타입 목록이 아니라 <b>실패 유형</b>으로 하는 이유는, 같은 예외
     * 타입이 상태·본문에 따라 다른 유형이 되기 때문이다 — 공급사 A 의 429 와 503 은 둘 다
     * {@code WebClientResponseException} 이지만 하나는 재시도하고 하나는 하지 않는다.
     *
     * <p>{@code CallNotPermittedException} 제외는 연산자 순서의 <b>필수 짝</b>이다. 재시도가 서킷
     * 바깥이라 이 예외가 재시도에 노출되고, 빼지 않으면 재시도가 열린 서킷을 계속 두드린다. 판정
     * predicate 가 이미 {@code CIRCUIT_OPEN} 을 재시도 대상에서 빼지만, 의도를 코드에 남기려고
     * 명시한다 (D-F9-4).
     */
    public RetryConfig toRetryConfig() {
        return RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .intervalFunction(
                        IntervalFunction.ofExponentialRandomBackoff(
                                minBackoff, BACKOFF_MULTIPLIER, jitterFactor, maxBackoff))
                .retryOnException(cause -> SupplierFailurePolicy.isRetryable(FailureClassifier.classifyQuietly(cause)))
                .ignoreExceptions(CallNotPermittedException.class)
                .build();
    }

    /**
     * 서킷 설정. 라이브러리 기본값을 그대로 쓰는 항목은 손대지 않는다 — 다만 창의 종류만은 기본값과
     * 같아도 명시한다. TIME_BASED 로 바꾸면 {@code minimumNumberOfCalls} 가 "창 기간 안에" 채워져야
     * 하는 수가 되어, 분당 검색이 적으면 <b>공급사가 완전히 죽어도 실패율이 계산되지 않는다</b>
     * (D-F9-13). 기본값이라 지워도 된다고 판단할 여지를 남기지 않으려고 적어 둔다.
     *
     * <p>느린 호출 판정({@code slowCall*})은 쓰지 않는다. 그 일은 시도별 상한이 이미 하고 있고 —
     * 그보다 느린 호출은 {@code TimeoutException} 이 되어 실패 표본으로 기록된다 — 같은 일을 두
     * 손잡이로 하면 어느 쪽이 걸렸는지 로그에서 구분되지 않는다.
     */
    public CircuitBreakerConfig toCircuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumNumberOfCalls)
                .failureRateThreshold(failureRateThreshold)
                .waitDurationInOpenState(waitDurationInOpenState)
                .permittedNumberOfCallsInHalfOpenState(permittedCallsInHalfOpen)
                .recordException(
                        cause -> SupplierFailurePolicy.isCircuitFailure(FailureClassifier.classifyQuietly(cause)))
                .build();
    }

    /**
     * 재시도가 실제로 기다릴 수 있는 최대 시간. 라이브러리가 지터를 <b>먼저</b> 얹고 그 결과를
     * {@code maxBackoff} 로 자르므로(‌{@code IntervalFunction.ofExponentialRandomBackoff}) 여기서도
     * 같은 순서로 계산한다 — 순서를 뒤집으면 유도값이 실제보다 크게 나와 상한이 새어 나간다.
     *
     * <p>기다리는 횟수가 {@code maxAttempts - 1} 인 이유는 {@code maxAttempts} 가 재시도 횟수가 아니라
     * <b>최초 호출을 포함한 총 시도 횟수</b>이기 때문이다.
     */
    private Duration worstCaseBackoffTotal() {
        IntervalFunction growth = IntervalFunction.ofExponentialBackoff(minBackoff, BACKOFF_MULTIPLIER);
        long totalMillis = 0;
        for (int attempt = 1; attempt < maxAttempts; attempt++) {
            long jittered = Math.round(growth.apply(attempt) * (1 + jitterFactor));
            totalMillis += Math.min(jittered, maxBackoff.toMillis());
        }
        return Duration.ofMillis(totalMillis);
    }
}
