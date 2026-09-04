package com.stay.property.infrastructure;

import com.stay.property.domain.Room;
import com.stay.property.domain.RoomRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomJpaRepository extends JpaRepository<Room, Long>, RoomRepository {
}
