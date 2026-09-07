package com.stay.property.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 수집용 조합기의 상한 설정. 검색용 {@link FanOutProperties} 와 형태·검사가 같고 prefix 만 다르다 —
 * 검색은 사용자가 기다리고 수집은 배치가 기다리므로 한 벌을 나눠 쓰면 어느 한쪽은 반드시 틀린다.
 *
 * <p>강제하는 조건도 같다 — 동시 상한이 요청마다 호출 수라 웨이브가 1 이고(D-F9-5), 그래서
 * {@code budget > per-call} 이 최소 조건이 아니라 <b>완전한 조건</b>이다.
 */
@ConfigurationProperties(prefix = CatalogFanOutProperties.PREFIX)
public record CatalogFanOutProperties(Duration perCall, Duration budget) {

    static final String PREFIX = "supplier.catalog.fan-out";

    public CatalogFanOutProperties {
        if (budget.compareTo(perCall) <= 0) {
            throw new IllegalArgumentException(
                    "%s.budget 은 per-call 보다 커야 한다: budget=%s, per-call=%s"
                            .formatted(PREFIX, budget, perCall));
        }
    }

    public FanOutPolicy toPolicy() {
        return new FanOutPolicy(perCall, budget);
    }
}
