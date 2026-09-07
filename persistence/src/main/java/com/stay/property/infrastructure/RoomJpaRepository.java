package com.stay.property.infrastructure;

import com.stay.property.domain.Room;
import com.stay.property.domain.RoomRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomJpaRepository extends JpaRepository<Room, Long>, RoomRepository {

    /** {@link PropertyJpaRepository#saveAll(List)} 와 같은 이유의 다리 (D-F6-11). */
    @Override
    default List<Room> saveAll(List<Room> rooms) {
        return saveAll((Iterable<Room>) rooms);
    }
}
