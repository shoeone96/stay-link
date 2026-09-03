package com.stay.property.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoomTypeRepository {

    Optional<RoomType> findByPropertyIdAndSupplierRoomTypeCode(Long propertyId, String supplierRoomTypeCode);

    List<RoomType> findAllByPropertyIdIn(Collection<Long> propertyIds);

    RoomType save(RoomType roomType);
}
