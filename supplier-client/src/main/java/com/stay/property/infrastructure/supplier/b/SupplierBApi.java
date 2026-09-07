package com.stay.property.infrastructure.supplier.b;

import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import reactor.core.publisher.Mono;

/**
 * 공급사 B HTTP Interface. 그룹 {@code supplier-b} 에 등록되어 base-url·타임아웃·기본 헤더는 설정에서 온다.
 * B 는 실패도 HTTP 200 이라 응답 타입 하나가 성공·실패 본문을 모두 받는다.
 */
@HttpExchange
public interface SupplierBApi {

    @GetExchange("/b/api/properties")
    Mono<BPropertiesResponse> properties();
}
