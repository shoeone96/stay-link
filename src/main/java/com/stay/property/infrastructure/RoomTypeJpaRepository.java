package com.stay.property.infrastructure;

import com.stay.property.domain.RoomType;
import com.stay.property.domain.RoomTypeRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomTypeJpaRepository extends JpaRepository<RoomType, Long>, RoomTypeRepository {
}
