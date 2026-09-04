package com.stay.mock.a;

import java.util.List;
import java.util.stream.Stream;

/**
 * A가 파는 숙소. 카탈로그가 런타임에 바뀌어도 읽는 쪽이 부분 갱신 중인 상태를 보지 않도록, 객실 목록을
 * 고쳐 쓰지 않고 새 인스턴스로 갈아 끼운다(copy-on-write).
 */
public record AProperty(String hotelCode, String hotelName, List<ARoom> roomTypes) {

    public AProperty {
        roomTypes = List.copyOf(roomTypes);
    }

    public AProperty withRoom(ARoom room) {
        return new AProperty(hotelCode, hotelName, Stream.concat(roomTypes.stream(), Stream.of(room)).toList());
    }

    public AProperty withoutRoom(String roomTypeCode) {
        List<ARoom> remaining = roomTypes.stream()
                .filter(room -> !room.roomTypeCode().equals(roomTypeCode))
                .toList();
        return new AProperty(hotelCode, hotelName, remaining);
    }
}
