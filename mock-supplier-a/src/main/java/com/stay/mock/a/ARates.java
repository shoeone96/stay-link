package com.stay.mock.a;

import java.time.LocalDate;

/**
 * A의 요금·재고 파생. 순수 함수이며 아무것도 의존하지 않는다.
 *
 * <p>배수를 double로 곱하지 않는 이유는 부동소수점 오차 때문이다 — {@code 110000 * 1.3}은
 * 143000.00000000003로 나오고, 값에 따라 반대로 내려가면 원 단위 절사에서 1원이 깎인다.
 * 정수 곱셈 뒤 나눗셈이 곧 원 단위 절사다.
 */
public final class ARates {

    private static final int PEAK_SURCHARGE_NUMERATOR = 13;
    private static final int PEAK_SURCHARGE_DENOMINATOR = 10;
    private static final int TAX_DIVISOR = 10;
    private static final int PEAK_INVENTORY_CAP = 1;
    private static final int SOLD_OUT_INVENTORY = 0;

    private ARates() {
    }

    public static int nightlyRate(ARoom room, LocalDate date) {
        if (!Nights.isPeak(date)) {
            return room.netRate();
        }
        return room.netRate() * PEAK_SURCHARGE_NUMERATOR / PEAK_SURCHARGE_DENOMINATOR;
    }

    public static int taxAmount(int nightlyRate) {
        return nightlyRate / TAX_DIVISOR;
    }

    /**
     * 품절이 주말 할증보다 앞선다 — 품절일이면 요일과 무관하게 0이다.
     */
    public static int remainingRooms(ARoom room, LocalDate date) {
        if (isSoldOut(room, date)) {
            return SOLD_OUT_INVENTORY;
        }
        if (!Nights.isPeak(date)) {
            return room.baseInventory();
        }
        return Math.min(PEAK_INVENTORY_CAP, room.baseInventory());
    }

    private static boolean isSoldOut(ARoom room, LocalDate date) {
        return room.soldOutDay() != null && room.soldOutDay() == date.getDayOfMonth();
    }
}
