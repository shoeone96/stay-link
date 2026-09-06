package com.stay.property.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webclient.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.HttpServiceGroup.ClientType;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * 바깥으로 나가는 호출의 배선. 공급사마다 그룹을 따로 두는 이유는 그래야 커넥터가 갈려
 * {@code spring.http.serviceclient.<group>.*} 로 공급사별 타임아웃·기본 헤더를 줄 수 있어서다.
 *
 * <p>지금 두 그룹에는 등록된 인터페이스가 없다 — 공급사별 호출 인터페이스는 실패 표현이 서로
 * 달라 하나로 찍어낼 수 없고, 그 지식은 공급사를 붙이는 쪽에 있다. 여기서는 그 인터페이스가
 * 얹힐 자리와 커넥터 설정만 만들어 둔다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FanOutProperties.class)
@ImportHttpServices(group = SupplierHttpClientConfig.SUPPLIER_A_GROUP, clientType = ClientType.WEB_CLIENT)
@ImportHttpServices(group = SupplierHttpClientConfig.SUPPLIER_B_GROUP, clientType = ClientType.WEB_CLIENT)
public class SupplierHttpClientConfig {

    public static final String SUPPLIER_A_GROUP = "supplier-a";
    public static final String SUPPLIER_B_GROUP = "supplier-b";

    @Bean
    FanOutPolicy fanOutPolicy(FanOutProperties properties) {
        return properties.toPolicy();
    }

    @Bean
    FanOutExecutor fanOutExecutor(FanOutPolicy policy) {
        return new FanOutExecutor(policy);
    }

    /** 그룹마다 만들어지는 {@code WebClient} 에 로깅 필터를 얹는다. */
    @Bean
    WebClientCustomizer maskingWebClientCustomizer() {
        return builder -> builder.filter(new MaskingExchangeFilter());
    }
}
