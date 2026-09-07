package com.stay.property.infrastructure;

import com.stay.property.application.Money;
import com.stay.property.application.StayItem;
import com.stay.property.application.StaySearchResult;
import com.stay.property.application.SupplierOutcome;
import com.stay.property.application.SupplierStatus;
import com.stay.property.domain.Supplier;
import java.util.Currency;
import java.util.List;

/**
 * 왕복 대상 타입(설계 §3.6)을 전부 품은 결과 하나 — {@code Money(long, Currency)} · {@code Supplier} enum ·
 * {@code Long} 식별자 · {@code List} · boolean. 부분 실패 모양이라 OK 와 FAILED 가 함께 들어 있다.
 */
final class StaySearchResultFixture {

    private static final Currency KRW = Currency.getInstance("KRW");

    private StaySearchResultFixture() {
    }

    static StaySearchResult partiallyFailed() {
        return new StaySearchResult(
                List.of(new StayItem(
                        1L, "Haeundae Blue Hotel", 11L, "Ocean Double", 2, true, new Money(435_600L, KRW), 1, Supplier.A)),
                List.of(
                        new SupplierOutcome(Supplier.A, SupplierStatus.OK),
                        new SupplierOutcome(Supplier.B, SupplierStatus.FAILED)));
    }
}
