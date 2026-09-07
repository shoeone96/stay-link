package com.stay.property.infrastructure;

import com.stay.property.infrastructure.supplier.a.SupplierAApi;
import com.stay.property.infrastructure.supplier.b.SupplierBApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.support.WebClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.HttpServiceGroup.ClientType;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * 바깥으로 나가는 호출의 배선. 공급사마다 그룹을 따로 두는 이유는 그래야 커넥터가 갈려
 * {@code spring.http.serviceclient.<group>.*} 로 공급사별 타임아웃·기본 헤더를 줄 수 있어서다.
 *
 * <p>그룹마다 공급사 인터페이스 하나가 얹힌다. 실패 표현이 서로 달라(A 는 HTTP 상태, B 는 본문 코드)
 * 하나로 찍어낼 수 없으므로 인터페이스·DTO·번역기는 공급사별 하위 패키지에 있고, 여기는 배선만 한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({FanOutProperties.class, SupplierAvailabilityProperties.class})
@ImportHttpServices(
        group = SupplierHttpClientConfig.SUPPLIER_A_GROUP,
        clientType = ClientType.WEB_CLIENT,
        types = SupplierAApi.class)
@ImportHttpServices(
        group = SupplierHttpClientConfig.SUPPLIER_B_GROUP,
        clientType = ClientType.WEB_CLIENT,
        types = SupplierBApi.class)
public class SupplierHttpClientConfig {

    public static final String SUPPLIER_A_GROUP = "supplier-a";
    public static final String SUPPLIER_B_GROUP = "supplier-b";

    /** 조합기 빈이 검색용·수집용 둘이라 주입 지점은 이름으로 고른다. */
    public static final String FAN_OUT_EXECUTOR = "fanOutExecutor";

    @Bean
    FanOutPolicy fanOutPolicy(FanOutProperties properties) {
        return properties.toPolicy();
    }

    @Bean(FAN_OUT_EXECUTOR)
    FanOutExecutor fanOutExecutor(FanOutPolicy policy) {
        return new FanOutExecutor(policy);
    }

    /**
     * 공급사 그룹의 {@code WebClient} 에만 로깅 필터를 얹는다. {@code WebClientCustomizer} 빈으로
     * 붙이면 컨텍스트의 <b>모든</b> {@code WebClient.Builder} 에 적용되어, 공급사와 무관한 호출까지
     * "공급사 호출"로 기록된다. 지금은 다른 {@code WebClient} 가 없어 결과가 같지만, 로그 문구가
     * 거짓이 되는 것은 하나만 늘어도 시작된다.
     */
    @Bean
    WebClientHttpServiceGroupConfigurer maskingLogGroupConfigurer() {
        return groups ->
                groups.filterByName(SUPPLIER_A_GROUP, SUPPLIER_B_GROUP)
                        .forEachClient((group, builder) -> builder.filter(new MaskingExchangeFilter()));
    }
}
