package com.stay.mock.b.catalog;

import java.util.List;
import java.util.stream.Stream;

/**
 * B가 파는 숙소. 카탈로그가 런타임에 바뀌어도 읽는 쪽이 부분 갱신 중인 상태를 보지 않도록, 객실 목록을
 * 고쳐 쓰지 않고 새 인스턴스로 갈아 끼운다(copy-on-write).
 */
public record BProperty(String propertyId, String propertyName, List<BRoom> rooms) {

    public BProperty {
        rooms = List.copyOf(rooms);
    }

    public BProperty withRoom(BRoom room) {
        return new BProperty(propertyId, propertyName, Stream.concat(rooms.stream(), Stream.of(room)).toList());
    }

    public BProperty withoutRoom(String roomId) {
        List<BRoom> remaining = rooms.stream()
                .filter(room -> !room.roomId().equals(roomId))
                .toList();
        return new BProperty(propertyId, propertyName, remaining);
    }
}
