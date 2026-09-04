package com.stay.mock.a;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Component;

/**
 * A가 취급하는 숙소 목록. 조회가 압도적으로 많고 제어 호출은 드물어서, 읽기를 막는 대신 불변 값을 통째로
 * 갈아 끼운다(copy-on-write).
 */
@Component
public class ACatalog {

    private static final int STD_DBL_SOLD_OUT_DAY = 2;

    private static final List<AProperty> SEED = List.of(
            new AProperty("A-3201", "Haeundae Blue Hotel", List.of(
                    new ARoom("OCN-DBL", "Ocean Double", 2, 110_000, 3, null),
                    new ARoom("STD-TWN", "Standard Twin", 2, 90_000, 5, null))),
            new AProperty("A-3305", "Gangnam City Stay", List.of(
                    new ARoom("STD-DBL", "Standard Double", 3, 130_000, 2, STD_DBL_SOLD_OUT_DAY))));

    private final Map<String, AProperty> properties = new ConcurrentHashMap<>();

    public ACatalog() {
        SEED.forEach(property -> properties.put(property.hotelCode(), property));
    }

    /**
     * 검증 대본이 응답을 눈으로 대조하므로 해시 순서에 맡기지 않고 숙소 코드 순으로 고정한다.
     */
    public List<AProperty> all() {
        return properties.values().stream()
                .sorted(Comparator.comparing(AProperty::hotelCode))
                .toList();
    }

    /**
     * 시드에 없는 코드는 조용히 빠진다 — 계약에 그 경우의 오류가 없고, 공급사는 아는 것만 돌려준다.
     * 순서는 요청한 코드 순서를 따른다.
     */
    public List<AProperty> findAll(List<String> hotelCodes) {
        return hotelCodes.stream()
                .map(properties::get)
                .filter(Objects::nonNull)
                .toList();
    }

    public void addProperty(AProperty property) {
        properties.put(property.hotelCode(), property);
    }

    public void removeProperty(String hotelCode) {
        properties.remove(hotelCode);
    }

    public void addRoom(String hotelCode, ARoom room) {
        replaceProperty(hotelCode, property -> property.withRoom(room));
    }

    public void removeRoom(String hotelCode, String roomTypeCode) {
        replaceProperty(hotelCode, property -> property.withoutRoom(roomTypeCode));
    }

    public int hotelCount() {
        return properties.size();
    }

    public int roomTypeCount() {
        return properties.values().stream()
                .mapToInt(property -> property.roomTypes().size())
                .sum();
    }

    /**
     * 없는 숙소를 고치려는 조작은 조용히 넘기지 않는다. 조작자가 코드를 잘못 적은 것을 응답이 알려주지
     * 않으면, 카탈로그가 바뀌지 않은 이유를 대본에서 찾을 수 없다.
     */
    private void replaceProperty(String hotelCode, UnaryOperator<AProperty> change) {
        AProperty updated = properties.computeIfPresent(hotelCode, (code, property) -> change.apply(property));
        if (updated == null) {
            throw new InvalidRequestException(ErrorKind.INVALID_PARAMETER);
        }
    }
}
