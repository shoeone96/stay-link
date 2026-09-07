package com.stay.property.infrastructure;

import com.stay.property.domain.Supplier;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * 공급사 호출 하나에 재시도와 서킷을 입히는 데코레이터. 호출을 만드는 쪽(Fetcher)도 결과를 모으는
 * 쪽(조합기)도 이 장치를 모른다 — 정책이 바뀌어도 두 어댑터의 배선 한 줄만 본다.
 *
 * <p>인스턴스 하나가 <b>한 용도</b>(검색·수집)를 맡고 공급사마다 상태를 따로 갖는다. 두 공급사는
 * 독립된 사건이라 A 가 흔들려도 B 의 판정에 섞이면 안 되고, 같은 공급사라도 용도가 다르면 호출당
 * 상한이 달라 한 통계에 섞으면 느린 쪽이 빠른 쪽의 판정을 지배한다 (D-F9-7).
 */
public final class SupplierResilience {

    public static final String AVAILABILITY = "availability";
    public static final String CATALOG = "catalog";

    private static final Logger log = LoggerFactory.getLogger(SupplierResilience.class);

    private final Duration attemptTimeout;
    private final Map<Supplier, Retry> retries;
    private final Map<Supplier, CircuitBreaker> breakers;

    public SupplierResilience(String purpose, ResiliencePolicy policy, Duration perCall) {
        RetryRegistry retryRegistry = RetryRegistry.of(policy.toRetryConfig());
        CircuitBreakerRegistry breakerRegistry = CircuitBreakerRegistry.of(policy.toCircuitBreakerConfig());
        this.attemptTimeout = policy.attemptTimeout(perCall);
        this.retries = perSupplier(purpose, retryRegistry::retry);
        this.breakers = perSupplier(purpose, key -> logStateTransitions(breakerRegistry.circuitBreaker(key)));
    }

    /**
     * 공급사 하나의 호출에 상한·서킷·재시도를 이 순서로 입힌다. 순서가 곧 설계다 (설계 §3.2).
     *
     * <ul>
     *   <li>시도별 상한이 <b>맨 안쪽</b> — 재시도가 업스트림을 다시 구독하므로 시도마다 다시 걸리고,
     *       서킷보다 안쪽이라 잘린 호출이 서킷의 실패 표본이 된다. 그래야 느린 공급사가 서킷을 연다
     *   <li>재시도가 <b>맨 바깥</b> — 라이브러리가 문서화한 중첩 순서다(D-F9-4). 그 결과 서킷이 세는
     *       단위가 논리 호출이 아니라 <b>시도</b>가 되지만, 서킷이 보는 실패율은 시도별 실패율과
     *       정확히 같아 왜곡되지 않는다 (설계 §3.2.1)
     * </ul>
     *
     * <p>조합기의 {@code per-call} 상한과 실패 흡수는 이 체인 <b>바깥</b>에 있다. 재시도가 그 흡수보다
     * 안쪽에 있어야 실패가 값이 되기 전에 다시 시도할 수 있다.
     */
    public <T> Mono<T> decorate(Supplier supplier, Mono<T> source) {
        return source.timeout(attemptTimeout)
                .transformDeferred(CircuitBreakerOperator.of(breakers.get(supplier)))
                .transformDeferred(RetryOperator.of(retries.get(supplier)));
    }

    /**
     * 상태 전이를 남긴다. 서킷은 같은 입력에 다른 답을 주는 장치라(모달 동작), 전이가 기록되지 않으면
     * 운영자가 "왜 이 공급사가 빠졌는지"를 재구성할 수 없다 — 조합기가 예산 초과를 굳이 로그로 남기는
     * 것과 같은 논리다 (설계 §4.1). 공급사 장애는 우리 코드의 결함이 아니므로 error 가 아니라 warn 이다.
     */
    private static CircuitBreaker logStateTransitions(CircuitBreaker breaker) {
        breaker
                .getEventPublisher()
                .onStateTransition(
                        event ->
                                log.warn(
                                        "공급사 서킷 상태 전이 circuit={} {} -> {}",
                                        event.getCircuitBreakerName(),
                                        event.getStateTransition().getFromState(),
                                        event.getStateTransition().getToState()));
        return breaker;
    }

    /** 공급사마다 레지스트리에서 하나씩 꺼내 둔다. 조회를 호출 경로에서 하지 않으려고 생성 시점에 채운다. */
    private static <T> Map<Supplier, T> perSupplier(String purpose, Function<String, T> fromRegistry) {
        EnumMap<Supplier, T> registered = new EnumMap<>(Supplier.class);
        for (Supplier supplier : Supplier.values()) {
            registered.put(supplier, fromRegistry.apply(registryKey(supplier, purpose)));
        }
        return registered;
    }

    /**
     * 레지스트리 키. 공급사만으로 잡으면 {@code per-call 30s} 인 목록 호출과 {@code per-call 4s} 인
     * 재고 호출이 같은 창에 섞여, 느린 쪽이 빠른 쪽의 서킷을 연다 (D-F9-7). 이 문자열이 상태 전이
     * 로그에 그대로 실려 어느 공급사의 어느 용도가 움직였는지를 남긴다.
     */
    private static String registryKey(Supplier supplier, String purpose) {
        return "%s:%s".formatted(supplier, purpose);
    }
}
