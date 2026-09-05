package com.stay.mock.b.api;

import com.stay.mock.b.catalog.BProperty;
import com.stay.mock.b.catalog.BRates;
import com.stay.mock.b.catalog.BRoom;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

/**
 * 재고·요금 응답의 {@code data}. 항목은 (숙소, 객실 타입) 조합당 하나이며, 날짜별 요금과 세금 금액은
 * 제공하지 않고 기간 총액과 {@code taxIncluded}만 준다.
 *
 * <p>{@code date}는 {@code LocalDate}로 두고 직렬화를 기본 설정에 맡긴다 — 타임스탬프 대신
 * {@code "2026-09-10"} 문자열로 나가는지는 S-01 대본이 눈으로 확인한다.
 */
public record BSearchData(List<Item> items) {

    private static final String CURRENCY = "KRW";
    /** B의 요금은 언제나 세금 포함(gross)이다. */
    private static final boolean TAX_INCLUDED = true;

    public static BSearchData of(List<BProperty> properties, SearchQuery query, List<LocalDate> nights) {
        List<Item> items = properties.stream()
                .flatMap(property -> itemsOf(property, query.guests(), nights))
                .toList();
        return new BSearchData(items);
    }

    /**
     * 요청 인원을 수용하지 못하는 객실 타입은 항목에서 빠진다.
     */
    private static Stream<Item> itemsOf(BProperty property, int guests, List<LocalDate> nights) {
        return property.rooms().stream()
                .filter(room -> room.maxOccupancy() >= guests)
                .map(room -> toItem(property, room, nights));
    }

    private static Item toItem(BProperty property, BRoom room, List<LocalDate> nights) {
        List<Inventory> inventory = nights.stream()
                .map(night -> new Inventory(night, BRates.remainingRooms(room, night)))
                .toList();
        return new Item(property.propertyId(), property.propertyName(), room.roomId(), room.roomName(),
                room.maxOccupancy(), room.breakfastIncluded(), CURRENCY, BRates.totalPrice(room, nights),
                TAX_INCLUDED, inventory);
    }

    public record Item(String propertyId, String propertyName, String roomId, String roomName, int maxOccupancy,
            boolean breakfastIncluded, String currency, int totalPrice, boolean taxIncluded,
            List<Inventory> inventory) {
    }

    public record Inventory(LocalDate date, int remainingRooms) {
    }
}
