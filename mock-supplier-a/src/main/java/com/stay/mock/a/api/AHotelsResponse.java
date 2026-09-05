package com.stay.mock.a.api;

import com.stay.mock.a.catalog.AProperty;
import java.util.List;

/**
 * 숙소 목록 응답. 자바 필드명과 JSON 키를 같게 두어 {@code @JsonProperty}가 한 곳도 필요 없다 —
 * 필드 이름을 잘못 적으면 그 자체가 계약 위반으로 드러난다.
 *
 * <p>{@code roomTypes}는 이 목록 API에만 있고 요금·재고·조식 정보는 없다.
 */
public record AHotelsResponse(List<Hotel> items) {

    public static AHotelsResponse of(List<AProperty> properties) {
        return new AHotelsResponse(properties.stream().map(AHotelsResponse::toHotel).toList());
    }

    private static Hotel toHotel(AProperty property) {
        List<RoomType> roomTypes = property.roomTypes().stream()
                .map(room -> new RoomType(room.roomTypeCode(), room.roomTypeName(), room.maxOccupancy()))
                .toList();
        return new Hotel(property.hotelCode(), property.hotelName(), roomTypes);
    }

    public record Hotel(String hotelCode, String hotelName, List<RoomType> roomTypes) {
    }

    public record RoomType(String roomTypeCode, String roomTypeName, int maxOccupancy) {
    }
}
