package com.stay.property.application;

import java.util.Currency;

/**
 * 재고·요금 응답 항목 픽스처. 검색 유스케이스 테스트가 보는 것은 코드·이름·재고뿐이라, 나머지 값은
 * 불변식을 통과하는 고정값으로 채워 테스트마다 되풀이되지 않게 한다.
 */
final class AvailabilityOfferFixture {

    private static final Currency KRW = Currency.getInstance("KRW");
    private static final int DEFAULT_MAX_OCCUPANCY = 2;
    private static final long DEFAULT_AMOUNT = 435_600L;
    private static final int DEFAULT_BOOKABLE_ROOMS = 1;

    private AvailabilityOfferFixture() {
    }

    static AvailabilityOffer offer(String propertyCode, String propertyName, String roomCode, String roomName) {
        return offer(propertyCode, propertyName, roomCode, roomName, DEFAULT_BOOKABLE_ROOMS);
    }

    static AvailabilityOffer offer(
            String propertyCode, String propertyName, String roomCode, String roomName, int bookableRooms) {
        return new AvailabilityOffer(
                propertyCode,
                propertyName,
                roomCode,
                roomName,
                DEFAULT_MAX_OCCUPANCY,
                true,
                new Money(DEFAULT_AMOUNT, KRW),
                bookableRooms);
    }
}
