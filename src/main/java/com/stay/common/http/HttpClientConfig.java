package com.stay.common.http;

import io.netty.channel.ChannelOption;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
@EnableConfigurationProperties(HttpTimeoutProperties.class)
public class HttpClientConfig {

    /**
     * 타임아웃 값의 단일 원본을 {@link HttpTimeoutProperties} 하나로 두려고 커넥터를 직접 만든다.
     * Boot의 {@code spring.http.reactiveclient.*} 속성은 이 빈이 있으면 쓰이지 않는다.
     */
    @Bean
    ClientHttpConnector clientHttpConnector(HttpTimeoutProperties timeouts) {
        return new ReactorClientHttpConnector(HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) timeouts.connect().toMillis())
                .responseTimeout(timeouts.read())
                // 공급사 API 는 리다이렉트를 쓰지 않는 계약이다. 예상치 못한 경로로 요청이 나가는 것을 막는다 (D-F3a-4).
                .followRedirect(false));
    }

    @Bean
    SupplierClientFactory supplierClientFactory(WebClient.Builder builder, HttpTimeoutProperties timeouts,
            HttpCallLogFilter logFilter) {
        return new SupplierClientFactory(builder, timeouts.callBudget(), logFilter);
    }
}
