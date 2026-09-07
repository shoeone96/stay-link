package com.stay.property.application;

import com.stay.property.domain.Supplier;
import java.util.Objects;

/** 공급사 하나의 검색 결과 표기. 부분 실패 사실이 응답에서 드러나는 자리다. */
public record SupplierOutcome(Supplier supplier, SupplierStatus status) {

    public SupplierOutcome {
        Objects.requireNonNull(supplier, "supplier");
        Objects.requireNonNull(status, "status");
    }
}
