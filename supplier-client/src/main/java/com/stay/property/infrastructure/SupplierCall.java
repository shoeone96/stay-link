package com.stay.property.infrastructure;

import com.stay.property.domain.Supplier;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * 아직 구독되지 않은 공급사 호출 1건. {@code Mono} 가 등장하는 마지막 자리이며, 이 값을
 * 만드는 쪽이 원본 응답을 무엇으로 바꿀지({@code T})까지 정해서 넘긴다.
 *
 * <p>계약: {@code mono} 를 만드는 코드는 <b>본문에서 블로킹하면 안 된다.</b> {@code Mono} 가
 * 만들어지기 전에 막히면 호출 1건의 상한을 붙일 자리가 없어 어떤 장치도 그 호출을 자르지 못한다.
 */
public record SupplierCall<T>(Supplier supplier, Mono<T> mono) {

    public SupplierCall {
        Objects.requireNonNull(supplier, "supplier");
        Objects.requireNonNull(mono, "mono");
    }
}
