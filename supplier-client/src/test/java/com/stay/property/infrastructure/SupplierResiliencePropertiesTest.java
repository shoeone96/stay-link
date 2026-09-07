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
 * 재시도·서킷 값이 틀리면 증상이 요청이 들어온 뒤에야, 그것도 "가끔 이상하다"로 나타난다 — 지터가
 * 붙은 대기나 열리지 않는 서킷은 눈으로 보이지 않는다. 그래서 <b>기동 시점에</b> 막고, 메시지에는
 * 자바 필드명이 아니라 고칠 사람이 읽는 설정 키를 싣는다.
 */
class SupplierResiliencePropertiesTest {

    private static final String PREFIX = "supplier.resilience";

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(ResilienceConfiguration.class);

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
    @DisplayName("범위를 벗어난 값이 있으면 기동 시점에 바인딩이 실패하고 메시지에 그 설정 키가 있다")
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
    @DisplayName("값이 아예 빠져 있어도 기동 시점에 바인딩이 실패하고 메시지에 그 설정 키가 있다")
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

    /** 설계 §3.6 의 검색용 값에서 한 키만 뺀 목록. 뺀 자리는 테스트가 직접 채우거나 비워 둔다. */
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
    @EnableConfigurationProperties(SupplierResilienceProperties.class)
    static class ResilienceConfiguration {}
}
