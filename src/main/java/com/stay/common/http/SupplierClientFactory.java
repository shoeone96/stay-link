package com.stay.common.http;

import java.time.Duration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * 공급사별 base URL·인증 키를 받아 {@code @HttpExchange} 인터페이스의 구현체를 찍어낸다.
 *
 * <p>전역 {@code WebClient} 빈 하나로 못 박지 않는 이유는 용도(목록·가격)나 공급사가 늘 때
 * 인스턴스를 나눌 수 있게 열어 두기 위해서다. 지금 나누지는 않는다.
 */
public class SupplierClientFactory {

    static final String API_KEY_HEADER = "X-Api-Key";

    private final WebClient.Builder builder;
    private final Duration callBudget;
    private final HttpCallLogFilter logFilter;

    public SupplierClientFactory(WebClient.Builder builder, Duration callBudget, HttpCallLogFilter logFilter) {
        this.builder = builder;
        this.callBudget = callBudget;
        this.logFilter = logFilter;
    }

    public <T> T create(Class<T> httpInterface, String baseUrl, String apiKey) {
        WebClient webClient = builder.clone()
                .baseUrl(baseUrl)
                .defaultHeader(API_KEY_HEADER, apiKey)
                .filter(logFilter)
                .build();
        WebClientAdapter adapter = WebClientAdapter.create(webClient);
        // ② read 는 조각 사이 간격만 재므로 총 소요에 상한이 없다. ③ 을 여기서 건다.
        adapter.setBlockTimeout(callBudget);
        return HttpServiceProxyFactory.builderFor(adapter)
                .build()
                .createClient(httpInterface);
    }
}
