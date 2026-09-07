package com.stay.property.infrastructure;

import com.stay.property.application.AvailabilityOffer;
import com.stay.property.application.AvailabilityQuery;
import com.stay.property.domain.Supplier;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 공급사 하나의 재고·요금 호출. 구현이 곧 "이 공급사를 조회할 수 있다"는 등록이며, 공급사가 늘어도
 * 어댑터를 고치는 것이 아니라 구현이 하나 늘어난다.
 *
 * <p>목록 Fetcher 와 달리 <b>묶음 하나</b>를 받는다. 한 요청에 담을 수 있는 코드 수에 한도가 있어
 * 공급사 하나가 여러 호출이 되는데, 자르는 일을 여기서 하면 조합기의 호출당 상한이 묶음이 아니라
 * 그 공급사 전체에 걸려 묶음이 늘수록 상한이 저절로 조여진다(D-F5-5).
 *
 * <p>계약: {@link #call} 은 <b>모든 작업을 {@code Mono.defer} 안에</b> 넣는다. 프록시 호출·번역에서
 * 나는 예외가 전부 Mono 의 error 신호가 되어야 조합기가 값으로 흡수한다.
 */
public interface SupplierAvailabilityFetcher {

    Supplier supplier();

    Mono<List<AvailabilityOffer>> call(List<String> chunk, AvailabilityQuery query);
}
