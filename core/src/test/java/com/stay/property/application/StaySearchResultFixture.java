package com.stay.property.application;

import com.stay.property.domain.Supplier;
import java.util.Currency;
import java.util.List;

/** 캐시·판정 테스트가 같은 세 모양(전원 OK · 부분 실패 · 전원 FAILED)을 반복해서 쓰므로 모은다. */
final class StaySearchResultFixture {

    private static final Currency KRW = Currency.getInstance("KRW");

    private StaySearchResultFixture() {
    }

    static StaySearchResult allSucceeded() {
        return new StaySearchResult(
                List.of(item(1L, 11L, Supplier.A), item(2L, 22L, Supplier.B)),
                List.of(outcome(Supplier.A, SupplierStatus.OK), outcome(Supplier.B, SupplierStatus.OK)));
    }

    static StaySearchResult partiallyFailed() {
        return new StaySearchResult(
                List.of(item(1L, 11L, Supplier.A)),
                List.of(outcome(Supplier.A, SupplierStatus.OK), outcome(Supplier.B, SupplierStatus.FAILED)));
    }

    static StaySearchResult allFailed() {
        return new StaySearchResult(
                List.of(),
                List.of(outcome(Supplier.A, SupplierStatus.FAILED), outcome(Supplier.B, SupplierStatus.FAILED)));
    }

    static StayItem item(Long propertyId, Long roomId, Supplier supplier) {
        return new StayItem(
                propertyId,
                "Haeundae Blue Hotel",
                roomId,
                "Ocean Double",
                2,
                true,
                new Money(435_600L, KRW),
                1,
                supplier);
    }

    static SupplierOutcome outcome(Supplier supplier, SupplierStatus status) {
        return new SupplierOutcome(supplier, status);
    }
}
