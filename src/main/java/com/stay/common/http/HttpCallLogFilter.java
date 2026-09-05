package com.stay.common.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

/**
 * 공급사 호출 1건마다 메서드·URI·상태·소요시간을 한 줄로 남긴다.
 *
 * <p>{@code Mono} 를 반환하는 것은 Spring 이 정한 확장점의 계약이다. "애플리케이션 코드에 리액티브 타입을
 * 노출하지 않는다"는 원칙은 우리가 부르는 쪽에 대한 것이고, 이 클래스는 그 확장점을 구현하는 자리다.
 *
 * <p>남기는 것은 세 가지다 — 응답이 온 경우(DEBUG), 전송이 실패한 경우(WARN), 그리고 응답이 오는 도중
 * 호출이 끊긴 경우(WARN). 세 번째가 없으면 ③ 호출 예산이 자른 호출은 로그에 실패로 남지 않는다.
 * 예산은 이 필터 바깥(어댑터의 blockTimeout)에서 발동해 구독 취소로만 나타나기 때문이다.
 */
@Component
public class HttpCallLogFilter implements ExchangeFilterFunction {

    private static final Logger log = LoggerFactory.getLogger(HttpCallLogFilter.class);

    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final String MASKED_API_KEY = "****";
    private static final String ABSENT_API_KEY = "none";

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        // 구독 시점마다 다시 재야 한다. 필터 조립 시점에 재면 재시도·지연 구독에서 값이 어긋난다.
        return Mono.defer(() -> {
            long startedAt = System.nanoTime();
            return next.exchange(request)
                    .doOnNext(response -> logResponded(request, response, elapsedMillis(startedAt)))
                    .doOnError(cause -> logFailed(request, cause, elapsedMillis(startedAt)))
                    .doOnCancel(() -> logAborted(request, elapsedMillis(startedAt)));
        });
    }

    private void logResponded(ClientRequest request, ClientResponse response, long elapsedMillis) {
        log.debug("Supplier call responded: method={}, uri={}, status={}, elapsedMs={}, apiKey={}",
                request.method(), request.url(), response.statusCode().value(), elapsedMillis, apiKeyOf(request));
    }

    private void logFailed(ClientRequest request, Throwable cause, long elapsedMillis) {
        log.warn("Supplier call failed: method={}, uri={}, elapsedMs={}, apiKey={}, cause={}",
                request.method(), request.url(), elapsedMillis, apiKeyOf(request), cause.toString());
    }

    private void logAborted(ClientRequest request, long elapsedMillis) {
        log.warn("Supplier call aborted before completion: method={}, uri={}, elapsedMs={}, apiKey={}",
                request.method(), request.url(), elapsedMillis, apiKeyOf(request));
    }

    private String apiKeyOf(ClientRequest request) {
        return maskApiKey(request.headers().getFirst(SupplierClientFactory.API_KEY_HEADER));
    }

    /**
     * 일부만 남겨도 로그 수집기에 그대로 쌓이므로 값 전체를 가린다. 키가 붙었는지 여부만은 남긴다 —
     * 인증 실패를 조사할 때 "헤더가 없었는가"와 "키가 틀렸는가"를 구분해야 하기 때문이다 (D-F3a-5).
     */
    private String maskApiKey(String apiKey) {
        return apiKey == null || apiKey.isBlank() ? ABSENT_API_KEY : MASKED_API_KEY;
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / NANOS_PER_MILLI;
    }
}
