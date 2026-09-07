package com.stay.property.domain;

import java.util.List;

public interface RoomRepository {

    Room save(Room room);

    /** 신규 엔티티만 넘긴다. 이미 영속 상태인 것의 변경은 트랜잭션 커밋 시 dirty checking 으로 반영된다. */
    List<Room> saveAll(List<Room> rooms);

    /**
     * lifecycle 필터 없이 해당 숙소들의 객실 전부. INACTIVE 를 빼면 되살아날 객실이 insert 경로로 가
     * {@code uq_room_property_code} 에 걸린다 (D-F6-9).
     */
    List<Room> findAllByPropertyIdIn(List<Long> propertyIds);
}
