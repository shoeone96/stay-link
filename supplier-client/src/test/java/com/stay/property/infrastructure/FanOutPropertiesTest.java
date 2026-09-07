package com.stay.property.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * 예산 부등식을 바인딩 지점이 강제하는지 본다. 동시 상한이 요청마다 호출 수가 된 뒤로 웨이브가
 * 항상 1 이라(D-F9-5), {@code budget > per-call} 이 최소 조건이 아니라 <b>완전한 조건</b>이다.
 */
class FanOutPropertiesTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(FanOutPropertiesConfiguration.class);

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "예산이 호출당 상한과 같으면, 2s, 2s, supplier.fan-out.budget",
        "예산이 호출당 상한보다 작으면, 2s, 1s, supplier.fan-out.budget"
    })
    @DisplayName("정합성을 깬 설정값이면 기동 시점에 바인딩이 실패한다")
    void bind_withInconsistentValues_failsAtStartup(
            String shape, String perCall, String budget, String violatedKey) {
        // given
        ApplicationContextRunner context =
                runner.withPropertyValues(
                        "supplier.fan-out.per-call=" + perCall, "supplier.fan-out.budget=" + budget);

        // when · then
        context.run(loaded -> assertThat(loaded).getFailure().rootCause().hasMessageContaining(violatedKey));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(FanOutProperties.class)
    static class FanOutPropertiesConfiguration {}
}
