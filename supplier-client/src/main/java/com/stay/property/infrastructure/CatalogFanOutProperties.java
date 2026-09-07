package com.stay.property.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 수집용 조합기의 상한 설정. 검색용 {@link FanOutProperties} 와 형태·검사가 같고 prefix 만 다르다 —
 * 검색은 사용자가 기다리고 수집은 배치가 기다리므로 한 벌을 나눠 쓰면 어느 한쪽은 반드시 틀린다.
 *
 * <p>여기서 강제하는 것도 최소 조건 {@code budget > per-call} 하나다. 지켜야 하는 부등식은
 * {@code budget > ⌈공급사 수 ÷ max-concurrent⌉ × per-call} 이고 값은 실측 뒤 다시 잡는다.
 */
@ConfigurationProperties(prefix = CatalogFanOutProperties.PREFIX)
public record CatalogFanOutProperties(int maxConcurrent, Duration perCall, Duration budget) {

    static final String PREFIX = "supplier.catalog.fan-out";

    public CatalogFanOutProperties {
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
