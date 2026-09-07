package com.stay.property.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 서버 없이 컨텍스트만 올려 검색용·수집용 조합기가 각자의 정책으로 뜨는지 본다. 조합기는 정책을
 * 밖으로 내지 않으므로(그럴 호출자가 없다) 필드를 직접 읽는다 — F3a 클래스를 테스트 때문에 바꾸지 않는다.
 */
@SpringBootTest(
        classes = SupplierCatalogConfigTest.CatalogConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SupplierCatalogConfigTest {

    @Autowired private Map<String, FanOutExecutor> executors;

    @Test
    @DisplayName("컨텍스트를 띄우면 조합기 빈이 둘이고 두 정책이 서로 다르다")
    void loadContext_registersTwoExecutorsWithDifferentPolicies() {
        // given · when — 컨텍스트 기동이 곧 실행이다

        // then
        assertThat(executors)
                .containsOnlyKeys("fanOutExecutor", "catalogFanOutExecutor")
                .extractingByKeys("fanOutExecutor", "catalogFanOutExecutor")
                .extracting(executor -> ReflectionTestUtils.getField(executor, "policy"))
                .doesNotHaveDuplicates();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({SupplierHttpClientConfig.class, SupplierCatalogConfig.class})
    static class CatalogConfiguration {}
}
