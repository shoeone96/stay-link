package com.stay.property.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 조합기의 상한 설정. 값이 정합하지 않으면 요청이 들어온 뒤가 아니라 <b>기동 시점에</b> 실패한다.
 *
 * <p>조합기가 동시 상한을 <b>요청마다 호출 수</b>로 잡은 뒤로(D-F9-5) 웨이브가 항상 1 이라, 지켜야
 * 하는 부등식이 {@code budget > per-call} 하나로 줄었다. 기동 시점에 공급사 수를 몰라 최소 조건만
 * 강제한다는 F3a 의 미완결이 여기서 닫힌다 — <b>최소 조건이 곧 완전한 조건</b>이다.
 */
@ConfigurationProperties(prefix = FanOutProperties.PREFIX)
public record FanOutProperties(Duration perCall, Duration budget) {

    static final String PREFIX = "supplier.fan-out";

    public FanOutProperties {
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
