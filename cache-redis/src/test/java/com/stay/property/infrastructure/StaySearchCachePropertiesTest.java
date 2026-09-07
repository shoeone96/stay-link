package com.stay.property.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** TTL 이 없거나 양수가 아니면 요청이 들어온 뒤가 아니라 기동 시점에 실패해야 한다 (F3a 와 같은 방식). */
class StaySearchCachePropertiesTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

    static Stream<Arguments> invalidTtls() {
        return Stream.of(
                Arguments.of("ttl 이 없으면", new String[] {"stay.search-cache.enabled=true"}),
                Arguments.of("ttl 이 0 이면", new String[] {"stay.search-cache.ttl=0s"}),
                Arguments.of("ttl 이 음수면", new String[] {"stay.search-cache.ttl=-1s"}));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTtls")
    @DisplayName("TTL 이 없거나 0 이하이면 기동 시점에 바인딩이 실패하고 메시지에 설정 키가 있다")
    void bind_withoutPositiveTtl_failsAtStartupNamingTheKey(String shape, String[] properties) {
        // given
        ApplicationContextRunner context = runner.withPropertyValues(properties);

        // when · then
        context.run(loaded -> assertThat(loaded).getFailure().rootCause().hasMessageContaining("stay.search-cache.ttl"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(StaySearchCacheProperties.class)
    static class PropertiesConfiguration {}
}
