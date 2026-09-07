package com.stay.property.infrastructure.supplier.a;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
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

    /**
     * 재고·요금 조회. 인자가 셋을 넘는 것은 <b>시그니처가 곧 HTTP 계약의 선언</b>이기 때문이다 —
     * record 로 묶으면 어떤 쿼리 파라미터가 나가는지가 다른 파일로 숨는다.
     *
     * @param hotelCodes 쉼표로 구분한 숙소 코드 목록. 한 요청에 담을 수 있는 개수는 계약 §5 ② 의
     *     한도를 따르며, 한도에 맞춰 자르는 일은 어댑터가 한다
     */
    @GetExchange("/a/v1/availability")
    Mono<AAvailabilityResponse> availability(
            @RequestParam("hotelCodes") String hotelCodes,
            @RequestParam("checkIn") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam("checkOut") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam("adults") int adults,
            @RequestParam("children") int children);
}
