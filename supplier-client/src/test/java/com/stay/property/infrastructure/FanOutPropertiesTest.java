package com.stay.property.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * 예산 부등식의 최소 조건을 바인딩 지점이 강제하는지 본다. 기동 시점에는 공급사 수를 알 수 없어
 * 부등식 전체를 검사할 수 없으므로, 여기서 막는 것은 {@code budget > per-call} 하나뿐이다.
 */
class FanOutPropertiesTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(FanOutPropertiesConfiguration.class);

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "예산이 호출당 상한과 같으면, 2, 2s, 2s, supplier.fan-out.budget",
        "예산이 호출당 상한보다 작으면, 2, 2s, 1s, supplier.fan-out.budget",
        "동시 호출 상한이 1보다 작으면, 0, 2s, 5s, supplier.fan-out.max-concurrent"
    })
    @DisplayName("정합성을 깬 설정값이면 기동 시점에 바인딩이 실패한다")
    void bind_withInconsistentValues_failsAtStartup(
            String shape, int maxConcurrent, String perCall, String budget, String violatedKey) {
        // given
        ApplicationContextRunner context =
                runner.withPropertyValues(
                        "supplier.fan-out.max-concurrent=" + maxConcurrent,
                        "supplier.fan-out.per-call=" + perCall,
                        "supplier.fan-out.budget=" + budget);

        // when · then
        context.run(loaded -> assertThat(loaded).getFailure().rootCause().hasMessageContaining(violatedKey));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(FanOutProperties.class)
    static class FanOutPropertiesConfiguration {}
}
