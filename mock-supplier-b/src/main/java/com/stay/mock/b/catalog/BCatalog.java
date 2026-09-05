package com.stay.mock.b.catalog;

import com.stay.mock.b.api.ErrorKind;
import com.stay.mock.b.api.InvalidRequestException;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * B가 취급하는 숙소 목록. H2 파일 DB에 담아 조작자가 {@code /h2-console}에서 직접 보고 지울 수 있게
 * 한다 (설계 3.6, D-F2-9). 읽는 쪽에는 불변 record 스냅샷만 돌려주므로 조회는 여전히 부분 갱신 중인
 * 상태를 보지 않는다.
 *
 * <p>{@code P-88410}은 A의 {@code A-3201}과 같은 숙소지만 둘을 잇는 공통 키는 두지 않는다 — 실제
 * 계약에 없고, 알려주지 않는 것이 정상 동작이다.
 */
@Component
public class BCatalog {

    private static final List<BProperty> SEED = List.of(
            new BProperty("P-88410", "Haeundae Blue Hotel", List.of(
                    new BRoom("R-201", "Ocean Double Room", 2, 126_000, 2, true),
                    new BRoom("R-305", "Family Suite", 4, 210_000, 3, true))));

    private final BPropertyRepository properties;
    private final BRoomRepository rooms;

    public BCatalog(BPropertyRepository properties, BRoomRepository rooms) {
        this.properties = properties;
        this.rooms = rooms;
    }

    /**
     * 테이블이 비어 있을 때만 시드를 넣는다. 조작자가 지운 것을 도구가 되살리면 조작이 무의미해진다 —
     * 시드로 되돌리는 방법은 DB 파일을 지우는 것이다.
     *
     * <p>{@code @Transactional}을 붙이지 않는다 — {@code @PostConstruct}는 프록시가 아니라 대상
     * 인스턴스에서 호출되어 트랜잭션이 걸리지 않는다. 붙이면 걸린 것처럼 보이기만 한다. 저장 한 건마다
     * 리포지토리 자신의 트랜잭션이 따로 열린다.
     */
    @PostConstruct
    public void seedIfEmpty() {
        if (properties.count() > 0) {
            return;
        }
        SEED.forEach(this::insert);
    }

    /**
     * 검증 대본이 응답을 눈으로 대조하므로 해시 순서에 맡기지 않고 숙소 코드 순으로 고정한다.
     */
    public List<BProperty> all() {
        return toProperties(properties.findAllByOrderByPropertyIdAsc());
    }

    /**
     * 시드에 없는 코드는 조용히 빠진다 — 계약에 그 경우의 오류가 없고, 공급사는 아는 것만 돌려준다.
     * 순서는 요청한 코드 순서를 따른다.
     */
    public List<BProperty> findAll(List<String> propertyIds) {
        Map<String, BProperty> found = toProperties(properties.findAllById(propertyIds)).stream()
                .collect(Collectors.toMap(BProperty::propertyId, Function.identity()));
        return propertyIds.stream()
                .map(found::get)
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional
    public void addProperty(BProperty property) {
        insert(property);
    }

    @Transactional
    public void removeProperty(String propertyId) {
        requireProperty(propertyId);
        rooms.deleteAllByPropertyId(propertyId);
        properties.deleteById(propertyId);
    }

    @Transactional
    public void addRoom(String propertyId, BRoom room) {
        requireProperty(propertyId);
        rooms.save(toEntity(propertyId, room));
    }

    /**
     * 있는 숙소라도 없는 객실 코드를 지우려는 조작은 거절한다. 손으로 코드를 타이핑하는 도구라 오타가 조용히
     * 통과하면, 카탈로그가 바뀌지 않은 이유를 대본에서 찾을 수 없다 (설계 3.5.9).
     */
    @Transactional
    public void removeRoom(String propertyId, String roomId) {
        requireProperty(propertyId);
        if (rooms.deleteAllByPropertyIdAndRoomId(propertyId, roomId) == 0) {
            throw new InvalidRequestException(ErrorKind.INVALID_PARAMETER);
        }
    }

    public int propertyCount() {
        return Math.toIntExact(properties.count());
    }

    public int roomCount() {
        return Math.toIntExact(rooms.count());
    }

    private void insert(BProperty property) {
        properties.save(new BPropertyEntity(property.propertyId(), property.propertyName()));
        property.rooms().forEach(room -> rooms.save(toEntity(property.propertyId(), room)));
    }

    /**
     * 없는 숙소를 고치려는 조작도 같은 이유로 거절한다.
     */
    private void requireProperty(String propertyId) {
        if (!properties.existsById(propertyId)) {
            throw new InvalidRequestException(ErrorKind.INVALID_PARAMETER);
        }
    }

    private List<BProperty> toProperties(List<BPropertyEntity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        Map<String, List<BRoom>> roomsByProperty = rooms
                .findAllByPropertyIdInOrderByIdAsc(entities.stream().map(BPropertyEntity::getPropertyId).toList())
                .stream()
                .collect(Collectors.groupingBy(BRoomEntity::getPropertyId,
                        Collectors.mapping(BCatalog::toRoom, Collectors.toList())));
        return entities.stream()
                .map(entity -> new BProperty(entity.getPropertyId(), entity.getPropertyName(),
                        roomsByProperty.getOrDefault(entity.getPropertyId(), List.of())))
                .toList();
    }

    private static BRoomEntity toEntity(String propertyId, BRoom room) {
        return new BRoomEntity(propertyId, room.roomId(), room.roomName(), room.maxOccupancy(), room.grossRate(),
                room.baseInventory(), room.breakfastIncluded());
    }

    private static BRoom toRoom(BRoomEntity entity) {
        return new BRoom(entity.getRoomId(), entity.getRoomName(), entity.getMaxOccupancy(), entity.getGrossRate(),
                entity.getBaseInventory(), entity.isBreakfastIncluded());
    }
}
