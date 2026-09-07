package com.stay.property.application;

import com.stay.property.domain.Property;
import com.stay.property.domain.Room;
import com.stay.property.domain.Supplier;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 검색 대상 매핑의 색인. 공급사에게 물어볼 코드 목록과, 돌아온 코드를 내부 식별자로 되돌리는 역매핑을
 * 함께 든다.
 *
 * <p>값 객체로 두는 이유는 클래스를 줄이는 것이 아니라 <b>미매핑 판정이라는 규칙이 붙기 때문</b>이다
 * (OOP-8). 유스케이스가 Map 세 개를 직접 굴리면 조립·색인·판정 셋을 한 클래스가 하게 되고
 * {@code if (id == null)} 이 흩어진다 (D-F7-2).
 */
public final class StayMappingIndex {

    private final Map<Supplier, List<String>> codesBySupplier;
    private final Map<PropertyKey, Long> propertyIds;
    private final Map<RoomKey, Long> roomIds;

    private StayMappingIndex(
            Map<Supplier, List<String>> codesBySupplier,
            Map<PropertyKey, Long> propertyIds,
            Map<RoomKey, Long> roomIds) {
        this.codesBySupplier = codesBySupplier;
        this.propertyIds = propertyIds;
        this.roomIds = roomIds;
    }

    /**
     * 넘어오는 것은 이미 ACTIVE 로 걸러진 매핑이다 (D-F7-5). 그래서 색인에 없다는 것은 "매핑이 없거나
     * 지금 팔지 않는다"를 함께 뜻하며, 이 값은 둘을 구분하지 않는다 — 둘 다 "지금 팔 수 없다"로 같고,
     * 구분하려면 색인에 INACTIVE 까지 실어야 해서 색인의 뜻이 흐려진다 (§3.6).
     */
    public static StayMappingIndex from(List<Property> properties, List<Room> rooms) {
        Map<Supplier, List<String>> codes = new EnumMap<>(Supplier.class);
        Map<PropertyKey, Long> propertyIds = new HashMap<>();
        for (Property property : properties) {
            codes.computeIfAbsent(property.supplier(), supplier -> new ArrayList<>())
                    .add(property.supplierPropertyCode());
            propertyIds.put(new PropertyKey(property.supplier(), property.supplierPropertyCode()), property.getId());
        }
        Map<RoomKey, Long> roomIds = new HashMap<>();
        for (Room room : rooms) {
            roomIds.put(new RoomKey(room.propertyId(), room.supplierRoomCode()), room.getId());
        }
        return new StayMappingIndex(freeze(codes), Map.copyOf(propertyIds), Map.copyOf(roomIds));
    }

    public Map<Supplier, List<String>> codesBySupplier() {
        return codesBySupplier;
    }

    public Optional<Long> propertyIdOf(Supplier supplier, String supplierPropertyCode) {
        return Optional.ofNullable(propertyIds.get(new PropertyKey(supplier, supplierPropertyCode)));
    }

    public Optional<Long> roomIdOf(Long propertyId, String supplierRoomCode) {
        return Optional.ofNullable(roomIds.get(new RoomKey(propertyId, supplierRoomCode)));
    }

    /**
     * 물어볼 곳이 없다는 뜻이다. 유스케이스는 이때 포트를 부르지 않는다 — 코드가 비면
     * {@link AvailabilityQuery} 의 불변식에 걸려 500 이 나가는데, 매핑이 빈 것은 앱을 처음 띄운
     * 정상 상태다 (D-F7-15).
     */
    public boolean isEmpty() {
        return codesBySupplier.isEmpty();
    }

    private static Map<Supplier, List<String>> freeze(Map<Supplier, List<String>> codes) {
        Map<Supplier, List<String>> frozen = new EnumMap<>(Supplier.class);
        codes.forEach((supplier, supplierCodes) -> frozen.put(supplier, List.copyOf(supplierCodes)));
        return Map.copyOf(frozen);
    }

    private record PropertyKey(Supplier supplier, String supplierPropertyCode) {
    }

    private record RoomKey(Long propertyId, String supplierRoomCode) {
    }
}
