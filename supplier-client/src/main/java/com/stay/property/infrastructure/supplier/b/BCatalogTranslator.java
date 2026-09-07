package com.stay.property.infrastructure.supplier.b;

import com.stay.property.application.CatalogProperty;
import com.stay.property.application.CatalogRoom;
import com.stay.property.domain.Supplier;
import com.stay.property.infrastructure.InvalidSupplierResponseException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 공급사 B 목록 응답을 표준 목록 모델로 옮긴다. 봉투 해체({@code resultCode}·{@code data})·계약 필수
 * 필드 확인·필드 대응만 하고 그 이상은 하지 않는다.
 *
 * <p>필수 필드는 공급사 계약의 이름(propertyId 등)으로 검사한다 — 어긋난 응답을 계약 문서와 대조하는
 * 쪽이 읽는 메시지이기 때문이다. 표준 모델이 자기 검증에서 던지는 범용 예외도 같은 전용 예외로 감싼다.
 * 그래야 분류기가 이 두 타입만 INVALID_RESPONSE 로 보고, 무관한 버그는 UNEXPECTED 로 남는다.
 *
 * <p>{@code rooms} 가 null 이거나 비어 있으면 객실 0개로 통과시키되 공급사 단위로 집계해 warn 을 남긴다.
 * 계약 문서에 "객실 없는 숙소" 표현이 없어 예외로 막을 근거가 없고, 계약이 바뀌면 전 숙소가 한꺼번에 0개가
 * 되어 이 로그로 드러난다. 예외로 바꿀지는 그 실측 뒤에 정한다.
 */
public class BCatalogTranslator {

    private static final Logger log = LoggerFactory.getLogger(BCatalogTranslator.class);

    private static final String SUCCESS_CODE = "0000";

    public List<CatalogProperty> translate(BPropertiesResponse response) {
        if (!SUCCESS_CODE.equals(response.resultCode())) {
            throw new SupplierBResultException(response.resultCode());
        }
        if (response.data() == null) {
            throw new InvalidSupplierResponseException(Supplier.B, "data is null");
        }
        List<BProperty> items = requireField(response.data().items(), "items");
        List<CatalogProperty> properties;
        try {
            properties = items.stream().map(BCatalogTranslator::toProperty).toList();
        } catch (IllegalArgumentException cause) {
            throw new InvalidSupplierResponseException(Supplier.B, cause.getMessage());
        }
        warnIfRoomless(properties);
        return properties;
    }

    private static void warnIfRoomless(List<CatalogProperty> properties) {
        long roomless = properties.stream().filter(property -> property.rooms().isEmpty()).count();
        if (roomless > 0) {
            log.warn(
                    "객실 정보가 없는 숙소가 있다 supplier={} roomless={} total={}",
                    Supplier.B,
                    roomless,
                    properties.size());
        }
    }

    private static CatalogProperty toProperty(BProperty property) {
        String code = requireField(property.propertyId(), "propertyId");
        String name = requireField(property.propertyName(), "propertyName");
        List<CatalogRoom> rooms =
                property.rooms() == null
                        ? List.of()
                        : property.rooms().stream().map(BCatalogTranslator::toRoom).toList();
        return new CatalogProperty(code, name, rooms);
    }

    private static CatalogRoom toRoom(BRoom room) {
        return new CatalogRoom(requireField(room.roomId(), "roomId"), requireField(room.roomName(), "roomName"));
    }

    private static String requireField(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw InvalidSupplierResponseException.missingField(Supplier.B, fieldName);
        }
        return value;
    }

    private static <T> List<T> requireField(List<T> value, String fieldName) {
        if (value == null) {
            throw InvalidSupplierResponseException.missingField(Supplier.B, fieldName);
        }
        return value;
    }
}
