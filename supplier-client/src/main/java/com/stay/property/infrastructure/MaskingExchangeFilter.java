package com.stay.property.infrastructure;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

/**
 * 바깥으로 나간 호출 1건마다 흔적을 남기는 필터. 인증 키는 값 자체를 지운다 — 로그가 그대로
 * 수집기로 흘러가므로 뒤 몇 자리를 남기는 것만으로도 유출 경로가 된다.
 */
public class MaskingExchangeFilter implements ExchangeFilterFunction {

    private static final Logger log = LoggerFactory.getLogger(MaskingExchangeFilter.class);

    /** 값이 곧 자격 증명인 헤더. 비교는 소문자로 맞춘 뒤 한다. */
    private static final Set<String> CREDENTIAL_HEADERS = Set.of("authorization", "x-api-key");

    private static final String MASK = "***";

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        long startedAt = System.nanoTime();
        log.info("공급사 호출 시작 {} {} headers=[{}]", request.method(), request.url(), maskedHeaders(request));
        return next.exchange(request)
                .doOnNext(
                        response ->
                                log.info(
                                        "공급사 호출 완료 {} {} status={} elapsed={}ms",
                                        request.method(),
                                        request.url(),
                                        response.statusCode().value(),
                                        elapsedMillis(startedAt)))
                .doOnError(
                        cause ->
                                log.warn(
                                        "공급사 호출 실패 {} {} elapsed={}ms 원인={}",
                                        request.method(),
                                        request.url(),
                                        elapsedMillis(startedAt),
                                        cause.toString()));
    }

    private static String maskedHeaders(ClientRequest request) {
        return request.headers().headerSet().stream()
                .map(header -> header.getKey() + "=" + maskedValue(header.getKey(), header.getValue()))
                .collect(Collectors.joining(", "));
    }

    private static String maskedValue(String name, List<String> values) {
        if (CREDENTIAL_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
            return MASK;
        }
        return String.join(",", values);
    }

    private static long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
