package com.stay.property.infrastructure.supplier.b;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
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

    /**
     * 재고·요금 조회. 인자가 셋을 넘는 것은 <b>시그니처가 곧 HTTP 계약의 선언</b>이기 때문이다 —
     * record 로 묶으면 어떤 쿼리 파라미터가 나가는지가 다른 파일로 숨는다.
     *
     * @param propertyIds 쉼표로 구분한 숙소 코드 목록. 한 요청에 담을 수 있는 개수는 계약 §6 ② 의
     *     한도를 따르며, 한도에 맞춰 자르는 일은 어댑터가 한다
     */
    @GetExchange("/b/api/search")
    Mono<BSearchResponse> search(
            @RequestParam("propertyIds") String propertyIds,
            @RequestParam("checkIn") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam("checkOut") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam("adults") int adults,
            @RequestParam("children") int children);
}
