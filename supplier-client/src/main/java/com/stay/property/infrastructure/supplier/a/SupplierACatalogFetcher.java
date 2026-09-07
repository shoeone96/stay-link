package com.stay.property.infrastructure.supplier.a;

import com.stay.property.application.CatalogProperty;
import com.stay.property.domain.Supplier;
import com.stay.property.infrastructure.SupplierCatalogFetcher;
import java.util.List;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class SupplierACatalogFetcher implements SupplierCatalogFetcher {

    private final SupplierAApi api;
    private final ACatalogTranslator translator = new ACatalogTranslator();

    public SupplierACatalogFetcher(SupplierAApi api) {
        this.api = api;
    }

    @Override
    public Supplier supplier() {
        return Supplier.A;
    }

    /** 프록시 호출까지 {@code defer} 안에 둔다 — 호출 시점의 동기 예외도 error 신호가 되어야 한다. */
    @Override
    public Mono<List<CatalogProperty>> call() {
        return Mono.defer(api::hotels).map(translator::translate);
    }
}
