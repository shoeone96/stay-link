package com.stay.mock.a.catalog;

import com.stay.mock.a.api.ErrorKind;
import com.stay.mock.a.api.InvalidRequestException;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * A가 취급하는 숙소 목록. H2 파일 DB에 담아 조작자가 {@code /h2-console}에서 직접 보고 지울 수 있게
 * 한다 (설계 3.6, D-F2-9). 읽는 쪽에는 불변 record 스냅샷만 돌려주므로 조회는 여전히 부분 갱신 중인
 * 상태를 보지 않는다.
 */
@Component
public class ACatalog {

    private static final int STD_DBL_SOLD_OUT_DAY = 2;

    /**
     * 조식은 {@code A-3201} 한 숙소 안에서 갈린다 — 재고·요금 조회 한 번에 {@code true}와
     * {@code false}가 같이 나와야, 조식을 축으로 쓰는 뒷단 기능이 한쪽 값만 보고 통과하지 못한다.
     */
    private static final List<AProperty> SEED = List.of(
            new AProperty("A-3201", "Haeundae Blue Hotel", List.of(
                    new ARoom("OCN-DBL", "Ocean Double", 2, 110_000, 3, null, true),
                    new ARoom("STD-TWN", "Standard Twin", 2, 90_000, 5, null, false))),
            new AProperty("A-3305", "Gangnam City Stay", List.of(
                    new ARoom("STD-DBL", "Standard Double", 3, 130_000, 2, STD_DBL_SOLD_OUT_DAY, false))));

    private final APropertyRepository properties;
    private final ARoomRepository rooms;

    public ACatalog(APropertyRepository properties, ARoomRepository rooms) {
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
    public List<AProperty> all() {
        return toProperties(properties.findAllByOrderByHotelCodeAsc());
    }

    /**
     * 시드에 없는 코드는 조용히 빠진다 — 계약에 그 경우의 오류가 없고, 공급사는 아는 것만 돌려준다.
     * 순서는 요청한 코드 순서를 따른다.
     */
    public List<AProperty> findAll(List<String> hotelCodes) {
        Map<String, AProperty> found = toProperties(properties.findAllById(hotelCodes)).stream()
                .collect(Collectors.toMap(AProperty::hotelCode, Function.identity()));
        return hotelCodes.stream()
                .map(found::get)
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional
    public void addProperty(AProperty property) {
        insert(property);
    }

    @Transactional
    public void removeProperty(String hotelCode) {
        requireProperty(hotelCode);
        rooms.deleteAllByHotelCode(hotelCode);
        properties.deleteById(hotelCode);
    }

    @Transactional
    public void addRoom(String hotelCode, ARoom room) {
        requireProperty(hotelCode);
        rooms.save(toEntity(hotelCode, room));
    }

    /**
     * 있는 숙소라도 없는 객실 코드를 지우려는 조작은 거절한다. 손으로 코드를 타이핑하는 도구라 오타가 조용히
     * 통과하면, 카탈로그가 바뀌지 않은 이유를 대본에서 찾을 수 없다 (설계 3.5.9).
     */
    @Transactional
    public void removeRoom(String hotelCode, String roomTypeCode) {
        requireProperty(hotelCode);
        if (rooms.deleteAllByHotelCodeAndRoomTypeCode(hotelCode, roomTypeCode) == 0) {
            throw new InvalidRequestException(ErrorKind.INVALID_PARAMETER);
        }
    }

    public int hotelCount() {
        return Math.toIntExact(properties.count());
    }

    public int roomTypeCount() {
        return Math.toIntExact(rooms.count());
    }

    private void insert(AProperty property) {
        properties.save(new APropertyEntity(property.hotelCode(), property.hotelName()));
        property.roomTypes().forEach(room -> rooms.save(toEntity(property.hotelCode(), room)));
    }

    /**
     * 없는 숙소를 고치려는 조작도 같은 이유로 거절한다.
     */
    private void requireProperty(String hotelCode) {
        if (!properties.existsById(hotelCode)) {
            throw new InvalidRequestException(ErrorKind.INVALID_PARAMETER);
        }
    }

    private List<AProperty> toProperties(List<APropertyEntity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        Map<String, List<ARoom>> roomsByHotel = rooms
                .findAllByHotelCodeInOrderByIdAsc(entities.stream().map(APropertyEntity::getHotelCode).toList())
                .stream()
                .collect(Collectors.groupingBy(ARoomEntity::getHotelCode,
                        Collectors.mapping(ACatalog::toRoom, Collectors.toList())));
        return entities.stream()
                .map(entity -> new AProperty(entity.getHotelCode(), entity.getHotelName(),
                        roomsByHotel.getOrDefault(entity.getHotelCode(), List.of())))
                .toList();
    }

    private static ARoomEntity toEntity(String hotelCode, ARoom room) {
        return new ARoomEntity(hotelCode, room.roomTypeCode(), room.roomTypeName(), room.maxOccupancy(),
                room.netRate(), room.baseInventory(), room.soldOutDay(), room.breakfastIncluded());
    }

    private static ARoom toRoom(ARoomEntity entity) {
        return new ARoom(entity.getRoomTypeCode(), entity.getRoomTypeName(), entity.getMaxOccupancy(),
                entity.getNetRate(), entity.getBaseInventory(), entity.getSoldOutDay(),
                entity.isBreakfastIncluded());
    }
}
