package com.stay.property.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** 수집용 정책도 검색용과 같은 조건({@code budget > per-call})을 기동 시점에 받는다. */
class CatalogFanOutPropertiesTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(CatalogFanOutPropertiesConfiguration.class);

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "예산이 호출당 상한과 같으면, 30s, 30s, supplier.catalog.fan-out.budget",
        "예산이 호출당 상한보다 작으면, 30s, 10s, supplier.catalog.fan-out.budget"
    })
    @DisplayName("수집 정책의 정합성을 깬 설정값이면 기동 시점에 바인딩이 실패한다")
    void bind_withInconsistentValues_failsAtStartup(
            String shape, String perCall, String budget, String violatedKey) {
        // given
        ApplicationContextRunner context =
                runner.withPropertyValues(
                        "supplier.catalog.fan-out.per-call=" + perCall,
                        "supplier.catalog.fan-out.budget=" + budget);

        // when · then
        context.run(loaded -> assertThat(loaded).getFailure().rootCause().hasMessageContaining(violatedKey));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CatalogFanOutProperties.class)
    static class CatalogFanOutPropertiesConfiguration {}
}
