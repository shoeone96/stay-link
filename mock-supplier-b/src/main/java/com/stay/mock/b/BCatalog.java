package com.stay.mock.b;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Component;

/**
 * B가 취급하는 숙소 목록. 조회가 압도적으로 많고 제어 호출은 드물어서, 읽기를 막는 대신 불변 값을 통째로
 * 갈아 끼운다(copy-on-write).
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

    private final Map<String, BProperty> properties = new ConcurrentHashMap<>();

    public BCatalog() {
        SEED.forEach(property -> properties.put(property.propertyId(), property));
    }

    /**
     * 검증 대본이 응답을 눈으로 대조하므로 해시 순서에 맡기지 않고 숙소 코드 순으로 고정한다.
     */
    public List<BProperty> all() {
        return properties.values().stream()
                .sorted(Comparator.comparing(BProperty::propertyId))
                .toList();
    }

    /**
     * 시드에 없는 코드는 조용히 빠진다 — 계약에 그 경우의 오류가 없고, 공급사는 아는 것만 돌려준다.
     * 순서는 요청한 코드 순서를 따른다.
     */
    public List<BProperty> findAll(List<String> propertyIds) {
        return propertyIds.stream()
                .map(properties::get)
                .filter(Objects::nonNull)
                .toList();
    }

    public void addProperty(BProperty property) {
        properties.put(property.propertyId(), property);
    }

    public void removeProperty(String propertyId) {
        properties.remove(propertyId);
    }

    public void addRoom(String propertyId, BRoom room) {
        replaceProperty(propertyId, property -> property.withRoom(room));
    }

    public void removeRoom(String propertyId, String roomId) {
        replaceProperty(propertyId, property -> property.withoutRoom(roomId));
    }

    public int propertyCount() {
        return properties.size();
    }

    public int roomCount() {
        return properties.values().stream()
                .mapToInt(property -> property.rooms().size())
                .sum();
    }

    /**
     * 없는 숙소를 고치려는 조작은 조용히 넘기지 않는다. 조작자가 코드를 잘못 적은 것을 응답이 알려주지
     * 않으면, 카탈로그가 바뀌지 않은 이유를 대본에서 찾을 수 없다.
     */
    private void replaceProperty(String propertyId, UnaryOperator<BProperty> change) {
        BProperty updated = properties.computeIfPresent(propertyId, (id, property) -> change.apply(property));
        if (updated == null) {
            throw new InvalidRequestException(ErrorKind.INVALID_PARAMETER);
        }
    }
}
