package com.stay.property.infrastructure;

import com.stay.property.application.CatalogProperty;
import com.stay.property.domain.Supplier;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 공급사 하나의 목록 호출. 구현이 곧 "이 공급사를 수집한다"는 등록이며, 어댑터는 구현 전부를 모아
 * 조합기에 한꺼번에 넣는다 — 고르는 것이 아니라 전부 실행하므로 선택 메서드가 없다.
 *
 * <p>계약: {@link #call()} 은 <b>모든 작업을 {@code Mono.defer} 안에</b> 넣는다. 프록시 호출·번역에서
 * 나는 예외가 전부 Mono 의 error 신호가 되어야 조합기가 값으로 흡수한다. Mono 를 만드는 동안 던진
 * 예외는 조합기 밖이라 잡을 곳이 없다.
 */
public interface SupplierCatalogFetcher {

    Supplier supplier();

    Mono<List<CatalogProperty>> call();
}
