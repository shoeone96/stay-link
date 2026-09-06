package com.stay.property.infrastructure;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
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
 * 바깥으로 <b>나간</b> 호출 1건마다 흔적을 남기는 필터. 인증 키는 값 자체를 지운다 — 로그가
 * 그대로 수집기로 흘러가므로 뒤 몇 자리를 남기는 것만으로도 유출 경로가 된다.
 *
 * <p>이 필터가 못 보는 것이 있다. 호출당 상한이나 전체 예산이 걸리면 상류가 <b>취소</b>되므로
 * 오류 신호가 오지 않아 {@code doOnError} 가 불리지 않는다. 그래서 <b>잘린 호출은 조합기가</b>
 * 기록한다 — 취소 이유(상한인지 예산인지)를 아는 층이 거기이기 때문이다.
 */
public class MaskingExchangeFilter implements ExchangeFilterFunction {

    private static final Logger log = LoggerFactory.getLogger(MaskingExchangeFilter.class);

    /** 값이 곧 자격 증명인 헤더. 비교는 소문자로 맞춘 뒤 한다. */
    private static final Set<String> CREDENTIAL_HEADERS = Set.of("authorization", "x-api-key");

    private static final String MASK = "***";

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        long startedAt = System.nanoTime();
        String url = maskedUrl(request.url());
        log.info("공급사 호출 시작 method={} url={} headers=[{}]", request.method(), url, maskedHeaders(request));
        return next.exchange(request)
                .doOnNext(
                        response ->
                                log.info(
                                        "공급사 호출 완료 method={} url={} status={} elapsedMs={}",
                                        request.method(),
                                        url,
                                        response.statusCode().value(),
                                        elapsedMillis(startedAt)))
                .doOnError(
                        cause ->
                                log.warn(
                                        "공급사 호출 실패 method={} url={} elapsedMs={} cause={}",
                                        request.method(),
                                        url,
                                        elapsedMillis(startedAt),
                                        cause.toString()));
    }

    /**
     * 쿼리 파라미터는 <b>이름만 남기고 값을 전부 지운다.</b> 어떤 이름이 자격 증명인지는 공급사마다
     * 다르고 지금은 공급사가 0곳이라 알 수 없는데, 이름으로 골라 가리면 목록에 없는 이름 하나로
     * 조용히 새기 시작한다. 값 대신 이름만 남겨도 어떤 파라미터를 보냈는지는 그대로 보인다.
     *
     * <p>사용자 정보(`user:password@host`)도 실릴 수 없도록 호스트를 authority 가 아니라
     * {@code host}·{@code port} 로 조립한다.
     */
    private static String maskedUrl(URI url) {
        StringBuilder masked = new StringBuilder(url.getScheme()).append("://").append(url.getHost());
        if (url.getPort() != -1) {
            masked.append(':').append(url.getPort());
        }
        masked.append(url.getRawPath());
        String query = url.getRawQuery();
        if (query != null && !query.isEmpty()) {
            masked.append('?').append(maskedQuery(query));
        }
        return masked.toString();
    }

    private static String maskedQuery(String rawQuery) {
        return Arrays.stream(rawQuery.split("&"))
                .map(pair -> pair.split("=", 2)[0] + "=" + MASK)
                .collect(Collectors.joining("&"));
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
