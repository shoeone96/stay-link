package com.stay.property.infrastructure.supplier.b;

import com.stay.property.application.CatalogProperty;
import com.stay.property.domain.Supplier;
import com.stay.property.infrastructure.SupplierCatalogFetcher;
import java.util.List;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class SupplierBCatalogFetcher implements SupplierCatalogFetcher {

    private final SupplierBApi api;
    private final BCatalogTranslator translator = new BCatalogTranslator();

    public SupplierBCatalogFetcher(SupplierBApi api) {
        this.api = api;
    }

    @Override
    public Supplier supplier() {
        return Supplier.B;
    }

    /** 프록시 호출까지 {@code defer} 안에 둔다 — 호출 시점의 동기 예외도 error 신호가 되어야 한다. */
    @Override
    public Mono<List<CatalogProperty>> call() {
        return Mono.defer(api::properties).map(translator::translate);
    }
}
