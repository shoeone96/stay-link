package com.stay.property.application;

import com.stay.property.domain.Supplier;
import java.util.List;
import java.util.Objects;

/**
 * 공급사 하나의 목록 호출 결과. 한 공급사의 실패가 다른 공급사의 결과나 호출 흐름을 끊지 않도록
 * 예외가 아니라 값으로 돌아온다.
 *
 * <p>{@link Fetched} 는 HTTP 성공 + 디코딩 + 번역 검증을 전부 통과한 뒤에만 만들어진다. 그래서 받는
 * 쪽은 {@code Fetched} 면 그 목록을 믿고 매핑을 갱신해도 되고, {@link Failed} 면 기존 매핑을 지킨다.
 */
public sealed interface SupplierCatalogResult {

    Supplier supplier();

    record Fetched(Supplier supplier, List<CatalogProperty> properties) implements SupplierCatalogResult {

        public Fetched {
            Objects.requireNonNull(supplier, "supplier");
            properties = List.copyOf(Objects.requireNonNull(properties, "properties"));
        }
    }

    record Failed(Supplier supplier, SupplierErrorCode reason) implements SupplierCatalogResult {

        public Failed {
            Objects.requireNonNull(supplier, "supplier");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
