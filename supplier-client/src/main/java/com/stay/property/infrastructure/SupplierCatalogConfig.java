package com.stay.property.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 수집용 조합기 빈. 검색용 {@code fanOutExecutor} 는 {@link SupplierHttpClientConfig} 에 그대로 두고,
 * 목록 어댑터만 이 빈을 이름으로 받는다. 정책 자체는 빈으로 올리지 않는다 — 정책 빈이 둘이 되면
 * 검색용 조합기의 주입이 모호해진다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CatalogFanOutProperties.class)
public class SupplierCatalogConfig {

    public static final String CATALOG_FAN_OUT_EXECUTOR = "catalogFanOutExecutor";

    @Bean(CATALOG_FAN_OUT_EXECUTOR)
    FanOutExecutor catalogFanOutExecutor(CatalogFanOutProperties properties) {
        return new FanOutExecutor(properties.toPolicy());
    }
}
