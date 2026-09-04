package com.stay.mock.b;

import java.util.List;

/**
 * 숙소 목록 응답의 {@code data}. 자바 필드명과 JSON 키를 같게 두어 {@code @JsonProperty}가 한 곳도
 * 필요 없다 — 필드 이름을 잘못 적으면 그 자체가 계약 위반으로 드러난다.
 *
 * <p>{@code rooms}는 이 목록 API에만 있고 요금·재고·조식 정보는 없다.
 */
public record BPropertiesData(List<Property> items) {

    public static BPropertiesData of(List<BProperty> properties) {
        return new BPropertiesData(properties.stream().map(BPropertiesData::toProperty).toList());
    }

    private static Property toProperty(BProperty property) {
        List<Room> rooms = property.rooms().stream()
                .map(room -> new Room(room.roomId(), room.roomName(), room.maxOccupancy()))
                .toList();
        return new Property(property.propertyId(), property.propertyName(), rooms);
    }

    public record Property(String propertyId, String propertyName, List<Room> rooms) {
    }

    public record Room(String roomId, String roomName, int maxOccupancy) {
    }
}
