package com.stay.property.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * 한 요청에 담을 수 있는 코드 수는 공급사별로 따로 받는다. 값이 없거나 0 이하면 묶음이 무한히 생기거나
 * 호출이 나가지 않으므로 요청이 들어온 뒤가 아니라 기동 시점에 실패해야 한다.
 */
class SupplierAvailabilityPropertiesTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(SupplierAvailabilityConfiguration.class);

    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenLimits")
    @DisplayName("공급사별 한도가 0 이하이거나 빠지면 그 공급사의 설정 키를 밝히며 기동이 실패한다")
    void bind_withMissingOrNonPositiveLimit_failsAtStartup(
            String shape, String[] properties, String violatedKey) {
        // given
        ApplicationContextRunner context = runner.withPropertyValues(properties);

        // when · then
        context.run(loaded -> assertThat(loaded).getFailure().rootCause().hasMessageContaining(violatedKey));
    }

    private static Stream<Arguments> brokenLimits() {
        return Stream.of(
                arguments(
                        "A 한도가 0",
                        new String[] {
                            "supplier.a.availability.max-codes=0", "supplier.b.availability.max-codes=50"
                        },
                        "supplier.a.availability.max-codes"),
                arguments(
                        "A 한도가 음수",
                        new String[] {
                            "supplier.a.availability.max-codes=-1", "supplier.b.availability.max-codes=50"
                        },
                        "supplier.a.availability.max-codes"),
                arguments(
                        "B 한도가 빠짐",
                        new String[] {"supplier.a.availability.max-codes=50"},
                        "supplier.b.availability.max-codes"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SupplierAvailabilityProperties.class)
    static class SupplierAvailabilityConfiguration {}
}
