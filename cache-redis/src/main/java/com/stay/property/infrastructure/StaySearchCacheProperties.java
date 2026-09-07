package com.stay.property.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 검색 결과 캐시 설정. TTL 은 정확성이 아니라 <b>절감 배수</b>의 값이라(§1.6) 코드가 아니라 설정에 두고,
 * 값이 없거나 0 이하면 요청이 들어온 뒤가 아니라 기동 시점에 실패한다 (F3a {@code FanOutProperties} 와
 * 같은 방식).
 *
 * @param ttl 저장 시점부터의 수명. 전원 FAILED 결과도 같은 TTL 이다 (D-F10-3)
 * @param enabled false 면 저장소 없이 뜬다 — 테스트와 배치가 쓴다. 기본 true
 */
@ConfigurationProperties(prefix = StaySearchCacheProperties.PREFIX)
public record StaySearchCacheProperties(Duration ttl, @DefaultValue("true") boolean enabled) {

    static final String PREFIX = "stay.search-cache";

    public StaySearchCacheProperties {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("%s.ttl 은 0 보다 커야 한다: %s".formatted(PREFIX, ttl));
        }
    }
}
