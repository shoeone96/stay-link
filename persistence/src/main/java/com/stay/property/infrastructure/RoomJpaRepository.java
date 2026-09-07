package com.stay.property.infrastructure;

import com.stay.property.domain.Room;
import com.stay.property.domain.RoomLifecycle;
import com.stay.property.domain.RoomRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomJpaRepository extends JpaRepository<Room, Long>, RoomRepository {

    /** {@link PropertyJpaRepository#saveAll(List)} 와 같은 이유의 다리 (D-F6-11). */
    @Override
    default List<Room> saveAll(List<Room> rooms) {
        return saveAll((Iterable<Room>) rooms);
    }

    /**
     * {@code uq_room_property_code} 의 선두 컬럼이 {@code property_id} 라 IN 조건이 그대로 그 인덱스를
     * 탄다. lifecycle 을 위한 인덱스는 두지 않는다 (D-F7-7).
     */
    @Override
    default List<Room> findAllSearchTargetsByPropertyIdIn(List<Long> propertyIds) {
        return findAllByPropertyIdInAndLifecycle(propertyIds, RoomLifecycle.ACTIVE);
    }

    List<Room> findAllByPropertyIdInAndLifecycle(List<Long> propertyIds, RoomLifecycle lifecycle);
}
