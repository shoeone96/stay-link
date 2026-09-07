package com.stay.property.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * 수집용 설정도 검색용과 같은 검사를 <b>자기 prefix 로</b> 받는지 본다. 두 벌이 같은 정책으로 가지만
 * 바인딩 지점은 둘이라, 한쪽만 검사를 걸어 두면 나머지 한쪽은 조용히 검사 없이 뜬다 — 기존
 * {@code CatalogFanOutPropertiesTest} 가 있는 이유와 같다.
 */
class CatalogResiliencePropertiesTest {

    private static final String PREFIX = "supplier.catalog.resilience";

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(CatalogResilienceConfiguration.class);

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({
        "max-attempts, 0",
        "min-backoff, 0ms",
        "max-backoff, 100ms",
        "jitter-factor, 1.0",
        "sliding-window-size, 0",
        "minimum-number-of-calls, 11",
        "failure-rate-threshold, 0",
        "wait-duration-in-open-state, 0s",
        "permitted-calls-in-half-open, 0"
    })
    @DisplayName("수집용도 범위를 벗어난 값이 있으면 기동 시점에 바인딩이 실패하고 메시지에 그 설정 키가 있다")
    void bind_withOutOfRangeValue_failsAtStartup(String key, String value) {
        // given
        ApplicationContextRunner context =
                runner.withPropertyValues(valuesExcept(key)).withPropertyValues("%s.%s=%s".formatted(PREFIX, key, value));

        // when · then
        context.run(
                loaded ->
                        assertThat(loaded).getFailure().rootCause().hasMessageContaining("%s.%s".formatted(PREFIX, key)));
    }

    @Test
    @DisplayName("수집용도 값이 아예 빠져 있으면 기동 시점에 바인딩이 실패하고 메시지에 그 설정 키가 있다")
    void bind_withMissingValue_failsAtStartup() {
        // given — 기간 값이 빠지면 null 로 바인딩되어 첫 호출에서야 터진다
        ApplicationContextRunner context = runner.withPropertyValues(valuesExcept("min-backoff"));

        // when · then
        context.run(
                loaded ->
                        assertThat(loaded)
                                .getFailure()
                                .rootCause()
                                .hasMessageContaining("%s.min-backoff".formatted(PREFIX)));
    }

    /** 한 키만 뺀 목록. 뺀 자리는 테스트가 직접 채우거나 비워 둔다. */
    private static String[] valuesExcept(String excluded) {
        List<String> values = new ArrayList<>();
        for (String pair :
                List.of(
                        "max-attempts=2",
                        "min-backoff=200ms",
                        "max-backoff=600ms",
                        "jitter-factor=0.5",
                        "sliding-window-size=10",
                        "minimum-number-of-calls=5",
                        "failure-rate-threshold=50",
                        "wait-duration-in-open-state=60s",
                        "permitted-calls-in-half-open=2")) {
            if (!pair.startsWith(excluded + "=")) {
                values.add("%s.%s".formatted(PREFIX, pair));
            }
        }
        return values.toArray(String[]::new);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CatalogResilienceProperties.class)
    static class CatalogResilienceConfiguration {}
}
