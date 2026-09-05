package com.stay.mock.a;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

/**
 * 재고·요금 응답. 항목은 (숙소, 객실 타입) 조합당 하나인 평평한 배열이고 {@code roomTypes}가 없다.
 *
 * <p>{@code date}는 {@code LocalDate}로 두고 직렬화를 기본 설정에 맡긴다 — 타임스탬프 대신
 * {@code "2026-09-10"} 문자열로 나가는지는 S-01 대본이 눈으로 확인한다.
 */
public record AAvailabilityResponse(List<Item> items) {

    private static final String CURRENCY = "KRW";

    public static AAvailabilityResponse of(List<AProperty> properties, SearchQuery query, List<LocalDate> nights) {
        List<Item> items = properties.stream()
                .flatMap(property -> itemsOf(property, query.guests(), nights))
                .toList();
        return new AAvailabilityResponse(items);
    }

    /**
     * 요청 인원을 수용하지 못하는 객실 타입은 항목에서 빠진다.
     */
    private static Stream<Item> itemsOf(AProperty property, int guests, List<LocalDate> nights) {
        return property.roomTypes().stream()
                .filter(room -> room.maxOccupancy() >= guests)
                .map(room -> toItem(property, room, nights));
    }

    private static Item toItem(AProperty property, ARoom room, List<LocalDate> nights) {
        List<DailyRate> dailyRates = nights.stream().map(night -> toDailyRate(room, night)).toList();
        return new Item(property.hotelCode(), property.hotelName(), room.roomTypeCode(), room.roomTypeName(),
                room.maxOccupancy(), room.breakfastIncluded(), CURRENCY, dailyRates);
    }

    private static DailyRate toDailyRate(ARoom room, LocalDate date) {
        int nightlyRate = ARates.nightlyRate(room, date);
        return new DailyRate(date, ARates.remainingRooms(room, date), nightlyRate, ARates.taxAmount(nightlyRate));
    }

    public record Item(String hotelCode, String hotelName, String roomTypeCode, String roomTypeName, int maxOccupancy,
            boolean breakfastIncluded, String currency, List<DailyRate> dailyRates) {
    }

    public record DailyRate(LocalDate date, int remainingRooms, int nightlyRate, int taxAmount) {
    }
}
