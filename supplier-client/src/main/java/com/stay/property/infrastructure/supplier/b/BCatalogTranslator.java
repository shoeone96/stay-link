package com.stay.property.infrastructure.supplier.b;

import com.stay.property.application.CatalogProperty;
import com.stay.property.application.CatalogRoom;
import com.stay.property.domain.Supplier;
import com.stay.property.infrastructure.InvalidSupplierResponseException;
import java.util.List;

/**
 * 공급사 B 목록 응답을 표준 목록 모델로 옮긴다. 봉투 해체({@code resultCode}·{@code data})·계약 필수
 * 필드 확인·필드 대응만 하고 그 이상은 하지 않는다.
 *
 * <p>필수 필드는 공급사 계약의 이름(propertyId 등)으로 검사한다 — 어긋난 응답을 계약 문서와 대조하는
 * 쪽이 읽는 메시지이기 때문이다. 표준 모델이 자기 검증에서 던지는 범용 예외도 같은 전용 예외로 감싼다.
 * 그래야 분류기가 이 두 타입만 INVALID_RESPONSE 로 보고, 무관한 버그는 UNEXPECTED 로 남는다.
 */
public class BCatalogTranslator {

    private static final String SUCCESS_CODE = "0000";

    public List<CatalogProperty> translate(BPropertiesResponse response) {
        if (!SUCCESS_CODE.equals(response.resultCode())) {
            throw new SupplierBResultException(response.resultCode());
        }
        if (response.data() == null) {
            throw new InvalidSupplierResponseException(Supplier.B, "data is null");
        }
        List<BProperty> properties = requireField(response.data().items(), "items");
        try {
            return properties.stream().map(BCatalogTranslator::toProperty).toList();
        } catch (IllegalArgumentException cause) {
            throw new InvalidSupplierResponseException(Supplier.B, cause.getMessage());
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
