package com.stay.property.infrastructure.supplier.b;

import com.stay.property.application.AvailabilityOffer;
import com.stay.property.application.AvailabilityQuery;
import com.stay.property.domain.Supplier;
import com.stay.property.infrastructure.SupplierAvailabilityFetcher;
import java.util.List;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class SupplierBAvailabilityFetcher implements SupplierAvailabilityFetcher {

    /** 계약 §6 ② 의 {@code propertyIds} 는 쉼표로 구분한 목록이다. */
    private static final String CODE_DELIMITER = ",";

    private final SupplierBApi api;
    private final BAvailabilityTranslator translator = new BAvailabilityTranslator();

    public SupplierBAvailabilityFetcher(SupplierBApi api) {
        this.api = api;
    }

    @Override
    public Supplier supplier() {
        return Supplier.B;
    }

    /** 프록시 호출까지 {@code defer} 안에 둔다 — 호출 시점의 동기 예외도 error 신호가 되어야 한다. */
    @Override
    public Mono<List<AvailabilityOffer>> call(List<String> chunk, AvailabilityQuery query) {
        return Mono.defer(
                        () ->
                                api.search(
                                        String.join(CODE_DELIMITER, chunk),
                                        query.checkIn(),
                                        query.checkOut(),
                                        query.adults(),
                                        query.children()))
                .map(response -> translator.translate(response, query));
    }
}
