package com.stay.property.infrastructure;

import com.stay.property.infrastructure.supplier.a.SupplierAApi;
import com.stay.property.infrastructure.supplier.b.SupplierBApi;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.autoconfigure.reactive.ClientHttpConnectorBuilderCustomizer;
import org.springframework.boot.http.client.reactive.ReactorClientHttpConnectorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ReactorResourceFactory;
import org.springframework.web.reactive.function.client.support.WebClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.HttpServiceGroup.ClientType;
import org.springframework.web.service.registry.ImportHttpServices;
import reactor.netty.resources.ConnectionProvider;

/**
 * 바깥으로 나가는 호출의 배선. 공급사마다 그룹을 따로 두는 이유는 그래야 커넥터가 갈려
 * {@code spring.http.serviceclient.<group>.*} 로 공급사별 타임아웃·기본 헤더를 줄 수 있어서다.
 *
 * <p>그룹마다 공급사 인터페이스 하나가 얹힌다. 실패 표현이 서로 달라(A 는 HTTP 상태, B 는 본문 코드)
 * 하나로 찍어낼 수 없으므로 인터페이스·DTO·번역기는 공급사별 하위 패키지에 있고, 여기는 배선만 한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
    FanOutProperties.class,
    SupplierAvailabilityProperties.class,
    SupplierResilienceProperties.class
})
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

    /** 조합기 빈이 검색용·수집용 둘이라 주입 지점은 이름으로 고른다. 데코레이터도 같은 이유로 이름을 갖는다. */
    public static final String FAN_OUT_EXECUTOR = "fanOutExecutor";

    public static final String SUPPLIER_RESILIENCE = "supplierResilience";

    /**
     * 커넥션 풀 크기. 동시 상한을 조합기에서 없앤 뒤로(D-F9-5) <b>바깥으로 나가는 호출의 유일한 상한</b>이
     * 여기다. 기본값을 그대로 두면 {@code max(코어 수, 8) × 2} 라 배포 머신마다 상한이 달라지므로 값을
     * 못박는다. 50 은 실측(동시 80 까지 p99 33ms, 선형)에서 여유가 확인된 구간이다.
     *
     * <p>이 값은 전체 상한이 아니라 <b>공급사 하나당</b> 상한이다 — reactor-netty 의 자바독이
     * {@code maxConnections} 를 "the maximum number of connections <b>(per connection pool)</b>" 로
     * 정의하고 풀은 원격 호스트별로 갈리므로, 공급사가 둘이면 천장은 50 이 아니라 호스트마다 50 이다.
     * 죽은 공급사가 자리를 붙들어도 그것은 그 공급사의 풀 안이라 살아 있는 공급사의 자리를 줄이지 않는다.
     * 검색 1건이 공급사마다 묶음 하나씩 내는 오늘 기준으로는 동시 검색 50건까지 대기 없이 흘린다.
     *
     * <p>자사 자원과 예상 동시 요청 수가 정해지면 다시 잡는다 — 설계 §6.1 의 이연 항목이다.
     */
    private static final int MAX_CONNECTIONS = 50;

    /**
     * 풀 자리를 기다리는 시간을 <b>시도별 상한</b>에서 나누는 몫. 기본값 45초를 그대로 두면 우리 상한이
     * <b>먼저</b> 터져 자사 병목이 {@code TIMEOUT} 으로 기록되고, 그 표본이 서킷을 열어 멀쩡한 공급사를
     * 차단한다.
     *
     * <p>기준이 조합기의 {@code per-call} 이 아닌 이유는, 재시도가 붙은 뒤로 호출을 먼저 자르는 것이
     * 바깥의 {@code per-call} 이 아니라 같은 체인 안쪽의 시도별 상한이기 때문이다 — 검색용에서
     * {@code per-call ÷ 2} 는 2s 인데 시도별 상한이 1.85s 라 풀 고갈이 그쪽에 먼저 잘린다 (D-F9-6).
     */
    private static final int PENDING_ACQUIRE_TIMEOUT_DIVISOR = 2;

    @Bean
    FanOutPolicy fanOutPolicy(FanOutProperties properties) {
        return properties.toPolicy();
    }

    @Bean(FAN_OUT_EXECUTOR)
    FanOutExecutor fanOutExecutor(FanOutPolicy policy) {
        return new FanOutExecutor(policy);
    }

    /**
     * 검색용 데코레이터. 시도별 상한이 {@code per-call} 에서 유도되므로 정책 빈을 함께 받는다 —
     * 그래야 "재시도 전체가 호출당 상한 안에서 끝난다"가 설정을 맞게 적었을 때만 성립하는 우연이 아니라
     * 구조가 된다 (설계 §3.2).
     */
    @Bean(SUPPLIER_RESILIENCE)
    SupplierResilience supplierResilience(SupplierResilienceProperties properties, FanOutPolicy policy) {
        return new SupplierResilience(SupplierResilience.AVAILABILITY, properties.toPolicy(), policy.perCall());
    }

    /**
     * 커넥션 풀을 명시 설정한다. 두 공급사 그룹의 커넥터가 이 자원을 함께 쓰지만 풀은 원격 호스트별로
     * 갈리므로 <b>크기</b>는 공급사마다 따로 걸린다. 반면 <b>대기 타임아웃</b>은 이 자원을 쓰는 모든
     * 경로에 같은 값 하나로 걸리므로, 데코레이터들이 든 시도별 상한 중 <b>가장 짧은 것</b>을 기준으로
     * 잡는다 — 한 경로에서라도 시도별 상한이 먼저 터지면 그 경로의 풀 고갈은 다시 공급사 실패로
     * 기록된다 (D-F9-6).
     */
    @Bean
    ReactorResourceFactory supplierConnectionResources(List<SupplierResilience> decorators) {
        ReactorResourceFactory resources = new ReactorResourceFactory();
        resources.setUseGlobalResources(false);
        resources.setConnectionProvider(
                ConnectionProvider.builder("supplier")
                        .maxConnections(MAX_CONNECTIONS)
                        .pendingAcquireTimeout(pendingAcquireTimeout(decorators))
                        .build());
        return resources;
    }

    @Bean
    ClientHttpConnectorBuilderCustomizer<ReactorClientHttpConnectorBuilder> supplierConnectorPoolCustomizer(
            ReactorResourceFactory resources) {
        return builder -> builder.withReactorResourceFactory(resources);
    }

    /** 데코레이터가 하나도 없으면 유도할 기준이 없다 — 배선이 틀린 것이라 기동을 세운다. */
    private static Duration pendingAcquireTimeout(List<SupplierResilience> decorators) {
        return decorators.stream()
                .map(SupplierResilience::attemptTimeout)
                .min(Duration::compareTo)
                .orElseThrow(() -> new IllegalStateException("공급사 데코레이터가 없어 풀 대기 시간을 유도할 수 없다"))
                .dividedBy(PENDING_ACQUIRE_TIMEOUT_DIVISOR);
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
