package com.stay.property.application;

import com.stay.property.domain.Room;
import java.lang.reflect.Field;

/** application 테스트용 객실 픽스처. id 를 채우는 이유는 {@link PropertyFixture} 와 같다. */
final class RoomFixture {

    private RoomFixture() {
    }

    static Room persisted(Long id, Long propertyId, String code, String name) {
        Room room = Room.create(propertyId, code, name);
        try {
            Field idField = Room.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(room, id);
            return room;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Room.id 를 채우지 못했다", e);
        }
    }
}
