package com.stay.property.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 조합기의 상한 설정. 값이 정합하지 않으면 요청이 들어온 뒤가 아니라 <b>기동 시점에</b> 실패한다.
 *
 * <p>기동 시점에는 공급사가 몇 곳인지 알 수 없으므로 여기서 강제하는 것은 최소 조건
 * {@code budget > per-call} 하나다. 실제로 지켜야 하는 부등식은 다음과 같고, 공급사를 붙이는
 * 쪽이 값을 다시 잡아야 한다.
 *
 * <pre>budget &gt; ⌈공급사 수 ÷ max-concurrent⌉ × per-call</pre>
 */
@ConfigurationProperties(prefix = FanOutProperties.PREFIX)
public record FanOutProperties(int maxConcurrent, Duration perCall, Duration budget) {

    static final String PREFIX = "supplier.fan-out";

    public FanOutProperties {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException(
                    "%s.max-concurrent 는 1 이상이어야 한다: %d".formatted(PREFIX, maxConcurrent));
        }
        if (budget.compareTo(perCall) <= 0) {
            throw new IllegalArgumentException(
                    "%s.budget 은 per-call 보다 커야 한다: budget=%s, per-call=%s"
                            .formatted(PREFIX, budget, perCall));
        }
    }

    public FanOutPolicy toPolicy() {
        return new FanOutPolicy(maxConcurrent, perCall, budget);
    }
}
