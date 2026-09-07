package com.stay.property.infrastructure.supplier.a;

import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import reactor.core.publisher.Mono;

/**
 * 공급사 A HTTP Interface. 그룹 {@code supplier-a} 에 등록되어 base-url·타임아웃·기본 헤더는 설정에서 온다.
 * A 는 실패를 HTTP 상태로 알리므로 실패 본문은 디코딩하지 않는다 — 분류는 상태만 쓰고, 본문은
 * {@code WebClientResponseException} 이 문자열로 들고 있어 로그에 충분하다.
 */
@HttpExchange
public interface SupplierAApi {

    @GetExchange("/a/v1/hotels")
    Mono<AHotelsResponse> hotels();
}
