package com.stay.property.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 수집용 조합기·데코레이터 빈. 검색용은 {@link SupplierHttpClientConfig} 에 그대로 두고, 목록
 * 어댑터만 이 빈들을 이름으로 받는다. 정책 자체는 빈으로 올리지 않는다 — 정책 빈이 둘이 되면
 * 검색용 조합기의 주입이 모호해진다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({CatalogFanOutProperties.class, CatalogResilienceProperties.class})
public class SupplierCatalogConfig {

    public static final String CATALOG_FAN_OUT_EXECUTOR = "catalogFanOutExecutor";

    public static final String CATALOG_SUPPLIER_RESILIENCE = "catalogSupplierResilience";

    @Bean(CATALOG_FAN_OUT_EXECUTOR)
    FanOutExecutor catalogFanOutExecutor(CatalogFanOutProperties properties) {
        return new FanOutExecutor(properties.toPolicy());
    }

    /**
     * 용도를 {@code catalog} 로 주는 것이 서킷 레지스트리 키를 검색용과 갈라 놓는 지점이다. 갈라 두지
     * 않으면 {@code per-call 30s} 인 목록 호출과 {@code per-call 4s} 인 재고 호출이 같은 실패율 창에
     * 섞여, 느린 목록 호출이 검색용 서킷을 연다 (D-F9-7).
     */
    @Bean(CATALOG_SUPPLIER_RESILIENCE)
    SupplierResilience catalogSupplierResilience(
            CatalogResilienceProperties properties, CatalogFanOutProperties fanOut) {
        return new SupplierResilience(SupplierResilience.CATALOG, properties.toPolicy(), fanOut.perCall());
    }
}
