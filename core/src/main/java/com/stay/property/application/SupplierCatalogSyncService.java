package com.stay.property.application;

import com.stay.property.domain.Property;
import com.stay.property.domain.PropertyRepository;
import com.stay.property.domain.Room;
import com.stay.property.domain.RoomRepository;
import com.stay.property.domain.Supplier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공급사 하나의 목록 응답을 매핑 테이블에 맞추는 트랜잭션 단위.
 *
 * <p>{@code REQUIRES_NEW} 인 이유: 호출자(배치 스텝)가 이미 트랜잭션 안에 있을 때 한 공급사의 실패가
 * 다른 공급사의 커밋까지 되돌리지 않게 하기 위해서다. 같은 트랜잭션에 참여하면 catch 를 해도 커밋 시
 * {@code UnexpectedRollbackException} 으로 전부 롤백된다 (D-F6-10).
 *
 * <p>순서가 계약이다 — 신규 숙소를 먼저 저장해야 그 id 로 객실을 만들 수 있다.
 */
@Service
public class SupplierCatalogSyncService {

    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;

    public SupplierCatalogSyncService(PropertyRepository propertyRepository, RoomRepository roomRepository) {
        this.propertyRepository = propertyRepository;
        this.roomRepository = roomRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sync(Supplier supplier, List<CatalogProperty> catalog) {
        List<Property> existingProperties = propertyRepository.findAllBySupplier(supplier);
        Map<Long, List<Room>> existingRoomsByPropertyId = findRoomsByPropertyId(existingProperties);

        deactivateMissingProperties(existingProperties, catalog, existingRoomsByPropertyId);
        Map<String, Property> propertiesByCode = applyProperties(supplier, catalog, existingProperties);
        applyRooms(catalog, propertiesByCode, existingRoomsByPropertyId);
    }

    /** 기존 숙소가 없으면 조회를 내보내지 않는다 — 빈 IN 절을 만들 이유가 없다. */
    private Map<Long, List<Room>> findRoomsByPropertyId(List<Property> properties) {
        if (properties.isEmpty()) {
            return Map.of();
        }
        List<Long> propertyIds = properties.stream().map(Property::getId).toList();
        return roomRepository.findAllByPropertyIdIn(propertyIds).stream()
                .collect(Collectors.groupingBy(Room::propertyId));
    }

    /** 응답에서 빠진 숙소는 판매 중단으로 기록하고, 그 숙소의 객실도 함께 내린다 (D-F6-4 쓰기 연쇄). */
    private void deactivateMissingProperties(
            List<Property> existingProperties,
            List<CatalogProperty> catalog,
            Map<Long, List<Room>> existingRoomsByPropertyId) {
        Set<String> codesInResponse = new HashSet<>();
        for (CatalogProperty catalogProperty : catalog) {
            codesInResponse.add(catalogProperty.code());
        }
        for (Property property : existingProperties) {
            if (codesInResponse.contains(property.supplierPropertyCode())) {
                continue;
            }
            property.deactivate();
            existingRoomsByPropertyId.getOrDefault(property.getId(), List.of()).forEach(Room::deactivate);
        }
    }

    /** 응답의 숙소를 기존과 맞추고, 응답 코드 → 숙소(기존 또는 방금 저장된 것) 색인을 돌려준다. */
    private Map<String, Property> applyProperties(
            Supplier supplier, List<CatalogProperty> catalog, List<Property> existingProperties) {
        Map<String, Property> propertiesByCode = new HashMap<>();
        for (Property property : existingProperties) {
            propertiesByCode.put(property.supplierPropertyCode(), property);
        }
        List<Property> newProperties = new ArrayList<>();
        for (CatalogProperty catalogProperty : catalog) {
            Property existing = propertiesByCode.get(catalogProperty.code());
            if (existing == null) {
                newProperties.add(Property.create(supplier, catalogProperty.code(), catalogProperty.name()));
                continue;
            }
            existing.activate();
            existing.rename(catalogProperty.name());
        }
        if (newProperties.isEmpty()) {
            return propertiesByCode;
        }
        for (Property saved : propertyRepository.saveAll(newProperties)) {
            propertiesByCode.put(saved.supplierPropertyCode(), saved);
        }
        return propertiesByCode;
    }

    private void applyRooms(
            List<CatalogProperty> catalog,
            Map<String, Property> propertiesByCode,
            Map<Long, List<Room>> existingRoomsByPropertyId) {
        List<Room> newRooms = new ArrayList<>();
        for (CatalogProperty catalogProperty : catalog) {
            // 객실 목록이 비어서 온 숙소는 판정을 건너뛴다. 응답 결함(공급사 쪽 warn, D-F3-9)일 수 있어
            // 그 숙소의 기존 객실을 전부 판매 중단으로 기록하지 않는다.
            if (catalogProperty.rooms().isEmpty()) {
                continue;
            }
            Long propertyId = propertiesByCode.get(catalogProperty.code()).getId();
            List<Room> existingRooms = existingRoomsByPropertyId.getOrDefault(propertyId, List.of());
            newRooms.addAll(diffRooms(propertyId, catalogProperty.rooms(), existingRooms));
        }
        if (!newRooms.isEmpty()) {
            roomRepository.saveAll(newRooms);
        }
    }

    /** 숙소 하나 안에서의 객실 세 갈래 판정. 신규만 돌려주고, 나머지는 영속 상태를 제자리에서 바꾼다. */
    private List<Room> diffRooms(Long propertyId, List<CatalogRoom> catalogRooms, List<Room> existingRooms) {
        Map<String, Room> roomsByCode = new HashMap<>();
        for (Room room : existingRooms) {
            roomsByCode.put(room.supplierRoomCode(), room);
        }
        List<Room> newRooms = new ArrayList<>();
        for (CatalogRoom catalogRoom : catalogRooms) {
            Room existing = roomsByCode.remove(catalogRoom.code());
            if (existing == null) {
                newRooms.add(Room.create(propertyId, catalogRoom.code(), catalogRoom.name()));
                continue;
            }
            existing.activate();
            existing.rename(catalogRoom.name());
        }
        roomsByCode.values().forEach(Room::deactivate);
        return newRooms;
    }
}
