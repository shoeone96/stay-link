package com.stay.property.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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
    @Autowired private Map<String, SupplierResilience> resiliences;

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

    /**
     * 두 빈이 <b>각자 자기 prefix 를 따라</b> 만들어지는지 본다. 유도된 시도별 상한으로 확인하는 이유는
     * 그 값 하나에 {@code per-call}·시도 수·백오프가 전부 들어가 있어, 한쪽 prefix 를 잘못 읽으면
     * 반드시 다른 수가 나오기 때문이다. 검색용은 (2s − 0.3s) ÷ 2, 수집용은 (3s − 0.75s) ÷ 3 이다.
     */
    @Test
    @DisplayName("컨텍스트를 띄우면 재시도·서킷 빈이 둘이고 각자 자기 설정에서 유도된 시도별 상한을 갖는다")
    void loadContext_registersTwoResiliencesFromTheirOwnPrefix() {
        // given · when — 컨텍스트 기동이 곧 실행이다

        // then
        assertThat(resiliences)
                .containsOnlyKeys("supplierResilience", "catalogSupplierResilience")
                .extractingByKeys("supplierResilience", "catalogSupplierResilience")
                .extracting(resilience -> ReflectionTestUtils.getField(resilience, "attemptTimeout"))
                .containsExactly(Duration.ofMillis(850), Duration.ofMillis(750));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({SupplierHttpClientConfig.class, SupplierCatalogConfig.class})
    static class CatalogConfiguration {}
}
