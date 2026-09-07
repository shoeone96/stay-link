package com.stay.property.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 수집용 재시도·서킷 설정. 검색용 {@link SupplierResilienceProperties} 와 형태·검사가 같고 prefix 만
 * 다르다 — 검색은 사용자가 기다리고 수집은 배치가 기다리므로 한 벌을 나눠 쓰면 어느 한쪽은 반드시
 * 틀린다. 수집은 지금 실패해도 다음 주기가 있어 시도를 더 줄 수 있다는 것이 값이 갈리는 이유다.
 *
 * <p>시도별 상한은 이 목록에 없다. 설정이 아니라 {@code per-call} 에서 유도되는 값이라
 * {@link ResiliencePolicy#attemptTimeout} 이 만들고, 유도가 불가능한 조합도 거기서 걸린다(설계 §3.2).
 */
@ConfigurationProperties(prefix = CatalogResilienceProperties.PREFIX)
public record CatalogResilienceProperties(
        int maxAttempts,
        Duration minBackoff,
        Duration maxBackoff,
        double jitterFactor,
        int slidingWindowSize,
        int minimumNumberOfCalls,
        float failureRateThreshold,
        Duration waitDurationInOpenState,
        int permittedCallsInHalfOpen) {

    static final String PREFIX = "supplier.catalog.resilience";

    /**
     * <b>이 값들로 정책을 만들 수 있는가</b>가 곧 이 설정의 정합성 조건이라, 만들어 보는 것으로
     * 검사한다. 검사 내용을 여기 복사하지 않는 이유는 수집용 설정도 같은 정책으로 가기 때문이다 —
     * 두 벌이 각자 검사하면 한쪽만 고쳐져 어긋난다. 여기서 하는 일은 그 메시지 앞에 고칠 사람이 읽는
     * 설정 키의 prefix 를 붙이는 것뿐이다.
     */
    public CatalogResilienceProperties {
        try {
            new ResiliencePolicy(
                    maxAttempts,
                    minBackoff,
                    maxBackoff,
                    jitterFactor,
                    failureRateThreshold,
                    slidingWindowSize,
                    minimumNumberOfCalls,
                    waitDurationInOpenState,
                    permittedCallsInHalfOpen);
        } catch (IllegalArgumentException violation) {
            // 원인을 사슬로 달지 않는다. 두 예외의 메시지가 같은 내용인데 사슬로 달면 root cause 가 키 없는
            // 쪽이 되고, 기동 실패 로그에서 사람이 가장 먼저 보는 마지막 줄에서 설정 키가 사라진다.
            throw new IllegalArgumentException("%s.%s".formatted(PREFIX, violation.getMessage()));
        }
    }

    public ResiliencePolicy toPolicy() {
        return new ResiliencePolicy(
                maxAttempts,
                minBackoff,
                maxBackoff,
                jitterFactor,
                failureRateThreshold,
                slidingWindowSize,
                minimumNumberOfCalls,
                waitDurationInOpenState,
                permittedCallsInHalfOpen);
    }
}
