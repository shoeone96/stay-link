package com.stay.mock.b.catalog;

import java.time.LocalDate;
import java.util.List;

/**
 * B의 요금·재고 파생. 순수 함수이며 아무것도 의존하지 않는다. B는 날짜별 단가를 주지 않고 기간 총액만
 * 주므로, 1박 요금은 밖으로 내보내지 않고 합산에만 쓴다.
 *
 * <p>배수를 double로 곱하지 않는 이유는 부동소수점 오차 때문이다 — 정수 곱셈 뒤 나눗셈이 곧 원 단위
 * 절사다.
 */
public final class BRates {

    private static final int PEAK_SURCHARGE_NUMERATOR = 13;
    private static final int PEAK_SURCHARGE_DENOMINATOR = 10;
    private static final int PEAK_INVENTORY_CAP = 1;

    private BRates() {
    }

    public static int totalPrice(BRoom room, List<LocalDate> nights) {
        return nights.stream()
                .mapToInt(night -> nightlyRate(room, night))
                .sum();
    }

    public static int remainingRooms(BRoom room, LocalDate date) {
        if (!Nights.isPeak(date)) {
            return room.baseInventory();
        }
        return Math.min(PEAK_INVENTORY_CAP, room.baseInventory());
    }

    private static int nightlyRate(BRoom room, LocalDate date) {
        if (!Nights.isPeak(date)) {
            return room.grossRate();
        }
        return room.grossRate() * PEAK_SURCHARGE_NUMERATOR / PEAK_SURCHARGE_DENOMINATOR;
    }
}
