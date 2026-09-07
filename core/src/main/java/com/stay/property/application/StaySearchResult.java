package com.stay.property.application;

import java.util.List;
import java.util.Objects;

/**
 * 검색 1건의 결과. <b>둘 다 비어도 된다</b> — 조회 대상이 하나도 없으면 공급사를 부르지 않고 빈 결과로
 * 돌아온다 (D-F7-15). 그때 {@code outcomes} 까지 비는 이유는 아무도 부르지 않았기 때문이다.
 */
public record StaySearchResult(List<StayItem> items, List<SupplierOutcome> outcomes) {

    public StaySearchResult {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        outcomes = List.copyOf(Objects.requireNonNull(outcomes, "outcomes"));
    }
}
